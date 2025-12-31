package com.zonerental.managers

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.zonerental.ZoneRental
import com.zonerental.async.*
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import com.zonerental.models.StorageGUISession
import java.util.EnumSet
import java.util.UUID

/**
 * Manages item storage from expired rentals and retrieval GUI.
 */
class StorageManager(private val plugin: ZoneRental) : Listener {

    private val activeGUISessions = mutableMapOf<UUID, StorageGUISession>()

    companion object {
        // Use StorageGUISession.ITEMS_PER_PAGE as single source of truth

        // Container types using EnumSet for O(1) contains() lookup
        private val CONTAINER_TYPES: Set<Material> = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BREWING_STAND,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
        )

        // Blocks that should NOT be stored (too common/cheap)
        private val DEFAULT_BLOCK_BLACKLIST: Set<Material> = EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.STONE,
            Material.COBBLESTONE,
            Material.GRAVEL,
            Material.SAND,
            Material.SANDSTONE,
            Material.WATER,
            Material.LAVA,
            Material.BEDROCK
        )
    }

    // Async scan service for optimized chunk-based scanning
    private val asyncScanService = AsyncScanService(plugin)

    /**
     * Gets the chunk count for a region, or null if region not found.
     * Used for region size validation before rental creation.
     */
    fun getRegionChunkCount(regionName: String, world: World): Int? {
        return asyncScanService.createScanRegion(regionName, world)?.chunkCount
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // ========================================
    // ASYNC SCANNING METHODS (ChunkSnapshot-based)
    // ========================================

    /**
     * Async version of collectItemsFromRegion using ChunkSnapshot pre-filtering.
     * Significantly reduces main thread calls for large regions.
     */
    suspend fun collectItemsFromRegionAsync(regionName: String, world: World): ScanResult<List<ItemStack>> {
        val startTime = System.currentTimeMillis()

        val scanRegion = asyncScanService.createScanRegion(regionName, world)
            ?: return ScanResult.Failure("Region not found: $regionName")

        // Use sync path for small regions (lower overhead)
        if (scanRegion.chunkCount < plugin.configManager.minChunksForAsync) {
            val items = collectItemsFromRegionSync(regionName, world)
            return ScanResult.Success(items)
        }

        asyncScanService.logRegionSizeWarning(scanRegion)

        val strategy = asyncScanService.determineStrategy(scanRegion)
        val batches = asyncScanService.calculateChunkBatches(scanRegion, strategy)

        val compositeKey = scanRegion.compositeKey
        val clipboard = plugin.worldEditManager.getClipboard(compositeKey)

        val allContainerLocations = mutableListOf<BlockCoord>()
        val allItems = mutableListOf<ItemStack>()
        val containerCounts = mutableMapOf<Material, Int>()

        // Phase A: Scan ChunkSnapshots for container locations
        // Note: getChunkSnapshot() loads chunks on main thread; snapshot analysis is fast
        for (batch in batches) {
            val adjusted = asyncScanService.tpsMonitor.adjustStrategy(strategy)
            if (adjusted.shouldPause) {
                delay(500)
            }

            for (chunkCoord in batch.chunks) {
                // Get snapshot (requires main thread for chunk loading)
                val snapshot = asyncScanService.getChunkSnapshot(world, chunkCoord.chunkX, chunkCoord.chunkZ)

                // Scan snapshot for containers (can run async)
                val containers = asyncScanService.scanSnapshotForContainers(snapshot, chunkCoord, scanRegion)
                allContainerLocations.addAll(containers)
            }

            asyncScanService.logScanProgress(regionName, batch.batchIndex, batch.totalBatches, allContainerLocations.size)

            if (adjusted.delayMs > 0 && batch.batchIndex < batch.totalBatches - 1) {
                delay(adjusted.delayMs)
            }
        }

        // Phase B: Access actual containers and collect items (main thread)
        withContext(plugin.minecraftDispatcher) {
            for (coord in allContainerLocations) {
                val block = world.getBlockAt(coord.x, coord.y, coord.z)

                // Skip shulker boxes - they preserve items when stored as blocks
                if (AsyncScanService.isShulkerBox(block.type)) continue

                val container = block.state as? Container ?: continue
                val inv = container.inventory

                // Collect items
                for (item in inv.contents) {
                    if (item != null && item.type != Material.AIR) {
                        allItems.add(item.clone())
                    }
                }

                // Determine if player-placed container
                val isPlayerPlaced = checkPlayerPlacedContainer(block, coord, clipboard)
                if (isPlayerPlaced) {
                    containerCounts[block.type] = containerCounts.getOrDefault(block.type, 0) + 1
                }

                // Clear container
                inv.clear()
                container.update()
            }
        }

        // Add player-placed containers as items
        for ((containerType, count) in containerCounts) {
            var remaining = count
            while (remaining > 0) {
                val stackSize = minOf(remaining, 64)
                allItems.add(ItemStack(containerType, stackSize))
                remaining -= stackSize
            }
        }

        val scanTime = System.currentTimeMillis() - startTime
        asyncScanService.logScanComplete(regionName, allContainerLocations.size, scanRegion.totalBlocks, scanTime)

        return ScanResult.Success(allItems)
    }

    /**
     * Async version of storePlayerBlocksFromRegion using ChunkSnapshot pre-filtering.
     */
    suspend fun storePlayerBlocksFromRegionAsync(regionName: String, world: World, playerUUID: UUID): List<ItemStack> {
        if (!plugin.configManager.isBlockStorage) {
            return emptyList()
        }

        val scanRegion = asyncScanService.createScanRegion(regionName, world)
            ?: return emptyList()

        // Use sync path for small regions
        if (scanRegion.chunkCount < plugin.configManager.minChunksForAsync) {
            return storePlayerBlocksFromRegionSync(regionName, world, playerUUID)
        }

        val compositeKey = scanRegion.compositeKey
        val clipboard = plugin.worldEditManager.getClipboard(compositeKey)
        if (clipboard == null) {
            plugin.logger.warning("No schematic found for region $regionName in world ${world.name} - cannot compare blocks")
            return emptyList()
        }

        val strategy = asyncScanService.determineStrategy(scanRegion)
        val batches = asyncScanService.calculateChunkBatches(scanRegion, strategy)
        val blockBlacklist = buildBlockBlacklist()

        val allChangedBlocks = mutableListOf<BlockCoord>()

        // Phase A: Scan ChunkSnapshots for changed blocks
        // Note: getChunkSnapshot() requires main thread; snapshot comparison is fast
        for (batch in batches) {
            val adjusted = asyncScanService.tpsMonitor.adjustStrategy(strategy)
            if (adjusted.shouldPause) {
                delay(500)
            }

            for (chunkCoord in batch.chunks) {
                val snapshot = asyncScanService.getChunkSnapshot(world, chunkCoord.chunkX, chunkCoord.chunkZ)
                val changedBlocks = asyncScanService.scanSnapshotForChangedBlocks(
                    snapshot, chunkCoord, scanRegion, clipboard, blockBlacklist
                )
                allChangedBlocks.addAll(changedBlocks)
            }

            if (adjusted.delayMs > 0 && batch.batchIndex < batch.totalBatches - 1) {
                delay(adjusted.delayMs)
            }
        }

        // Phase B: Convert changed blocks to items (main thread for block access)
        val playerBlocks = mutableListOf<ItemStack>()
        withContext(plugin.minecraftDispatcher) {
            for (coord in allChangedBlocks) {
                val block = world.getBlockAt(coord.x, coord.y, coord.z)
                val blockItem = blockToItemStack(block)
                if (blockItem != null) {
                    playerBlocks.add(blockItem)
                }
            }
        }

        if (plugin.configManager.isDebug) {
            plugin.logger.info("Block comparison for $regionName: ${scanRegion.totalBlocks} blocks scanned, ${playerBlocks.size} player-placed blocks stored")
        }

        if (playerBlocks.isNotEmpty()) {
            Bukkit.getPlayer(playerUUID)?.let { player ->
                if (player.isOnline) {
                    player.sendMessage("${ChatColor.GREEN}Stored ${playerBlocks.size} player-placed blocks from $regionName")
                }
            }
        }

        return playerBlocks
    }

    /**
     * Async version of storeItemsAndBlocksFromRegion.
     */
    suspend fun storeItemsAndBlocksFromRegionAsync(regionName: String, world: World, playerUUID: UUID) {
        val containerItemsResult = collectItemsFromRegionAsync(regionName, world)
        val containerItems = containerItemsResult.getOrNull() ?: emptyList()

        val playerBlocks = storePlayerBlocksFromRegionAsync(regionName, world, playerUUID)

        if (containerItems.isNotEmpty() || playerBlocks.isNotEmpty()) {
            // Store items using IO dispatcher for file operations
            withContext(Dispatchers.IO) {
                plugin.storageConfig.storeItems(playerUUID, regionName, containerItems, playerBlocks)
            }

            val totalItems = containerItems.size + playerBlocks.size

            Bukkit.getPlayer(playerUUID)?.let { player ->
                if (player.isOnline) {
                    player.sendMessage("${ChatColor.GREEN}Stored ${containerItems.size} items and ${playerBlocks.size} blocks from $regionName")

                    if (totalItems > StorageGUISession.ITEMS_PER_PAGE) {
                        val pages = (totalItems - 1) / StorageGUISession.ITEMS_PER_PAGE + 1
                        player.sendMessage("${ChatColor.YELLOW}Your items are stored across $pages pages. Use /zrretrieve to access them.")
                    }
                }
            }

            plugin.logger.info("Stored ${containerItems.size} items and ${playerBlocks.size} blocks from region $regionName")
        }
    }

    /**
     * Helper to check if a container block was player-placed.
     */
    private fun checkPlayerPlacedContainer(block: Block, coord: BlockCoord, clipboard: Clipboard?): Boolean {
        if (clipboard == null) return true

        val pos = BlockVector3.at(coord.x, coord.y, coord.z)
        val originalBlockState = clipboard.getBlock(pos)
        val originalBlockType = originalBlockState.blockType.id
        val originalMaterial = Material.matchMaterial(
            originalBlockType.replace("minecraft:", "").uppercase()
        )
        return originalMaterial == null || block.type != originalMaterial
    }

    // ========================================
    // SYNC SCANNING METHODS (Original implementation)
    // ========================================

    /**
     * Public API: Collects items from containers in a region.
     * Routes to async or sync implementation based on region size and config.
     */
    fun collectItemsFromRegion(regionName: String, world: World): List<ItemStack> {
        // Check if async scanning is enabled
        if (!plugin.configManager.isAsyncScanningEnabled) {
            return collectItemsFromRegionSync(regionName, world)
        }

        // Check region size to decide path
        val scanRegion = asyncScanService.createScanRegion(regionName, world)
        if (scanRegion == null || scanRegion.chunkCount < plugin.configManager.minChunksForAsync) {
            return collectItemsFromRegionSync(regionName, world)
        }

        // Use async path with blocking bridge for larger regions
        return runBlocking {
            when (val result = collectItemsFromRegionAsync(regionName, world)) {
                is ScanResult.Success -> result.data
                is ScanResult.PartialSuccess -> result.data
                is ScanResult.Failure -> {
                    plugin.logger.warning("Async scan failed: ${result.error}")
                    collectItemsFromRegionSync(regionName, world)
                }
            }
        }
    }

    /**
     * Synchronous version of collectItemsFromRegion.
     * Used for small regions or as fallback.
     */
    private fun collectItemsFromRegionSync(regionName: String, world: World): List<ItemStack> {
        val allItems = mutableListOf<ItemStack>()
        val containerCounts = mutableMapOf<Material, Int>()

        val regionManager = WorldGuard.getInstance().platform
            .regionContainer.get(BukkitAdapter.adapt(world)) ?: return allItems

        val region = regionManager.getRegion(regionName) ?: return allItems

        val compositeKey = "${world.name}:$regionName"
        val clipboard = plugin.worldEditManager.getClipboard(compositeKey)
        if (clipboard == null) {
            plugin.logger.warning("No schematic found for region $regionName in world ${world.name} - counting all containers")
        }

        val min = region.minimumPoint
        val max = region.maximumPoint

        for (x in min.x()..max.x()) {
            for (y in min.y()..max.y()) {
                for (z in min.z()..max.z()) {
                    val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    val block = loc.getBlock()

                    if (block.type in CONTAINER_TYPES) {
                        // Skip shulker boxes - they preserve items when stored as blocks
                        if (AsyncScanService.isShulkerBox(block.type)) continue

                        val container = block.state as? Container ?: continue
                        val inv = container.inventory

                        for (item in inv.contents) {
                            if (item != null && item.type != Material.AIR) {
                                allItems.add(item.clone())
                            }
                        }

                        // Determine if player-placed
                        var isPlayerPlaced = false
                        if (clipboard != null) {
                            val pos = BlockVector3.at(x, y, z)
                            val originalBlockState = clipboard.getBlock(pos)
                            val originalBlockType = originalBlockState.blockType.id
                            val originalMaterial = Material.matchMaterial(
                                originalBlockType.replace("minecraft:", "").uppercase()
                            )
                            if (originalMaterial == null || block.type != originalMaterial) {
                                isPlayerPlaced = true
                            }
                        } else {
                            isPlayerPlaced = true
                        }

                        if (isPlayerPlaced) {
                            containerCounts[block.type] = containerCounts.getOrDefault(block.type, 0) + 1
                        }

                        inv.clear()
                        container.update()
                    }
                }
            }
        }

        // Add clean container items to the list
        for ((containerType, count) in containerCounts) {
            var remaining = count
            while (remaining > 0) {
                val stackSize = minOf(remaining, 64)
                allItems.add(ItemStack(containerType, stackSize))
                remaining -= stackSize
            }
        }

        return allItems
    }

    @Deprecated("Use world-aware version", ReplaceWith("collectItemsFromRegion(regionName, world)"))
    fun collectItemsFromRegion(regionName: String): List<ItemStack> {
        val world = findWorldForRegion(regionName)
        if (world == null) {
            plugin.logger.warning("Could not find world for region $regionName")
            return emptyList()
        }
        return collectItemsFromRegion(regionName, world)
    }

    /**
     * Stores items from containers in a region.
     */
    fun storeItemsFromRegion(regionName: String, world: World, playerUUID: UUID) {
        val allItems = collectItemsFromRegion(regionName, world)

        if (allItems.isNotEmpty()) {
            plugin.storageConfig.storeItems(playerUUID, regionName, allItems)

            Bukkit.getPlayer(playerUUID)?.let { player ->
                if (player.isOnline) {
                    player.sendMessage(
                        plugin.configManager.getMessage("items-stored",
                            "{region}", regionName,
                            "{count}", allItems.size.toString())
                    )

                    if (allItems.size > StorageGUISession.ITEMS_PER_PAGE) {
                        val pages = (allItems.size - 1) / StorageGUISession.ITEMS_PER_PAGE + 1
                        player.sendMessage("${ChatColor.YELLOW}Your items are stored across $pages pages. Use /zrretrieve to access them.")
                    }
                }
            }

            plugin.logger.info("Stored ${allItems.size} items from region $regionName")
        }
    }

    /**
     * Stores both container items and player-placed blocks together.
     */
    fun storeItemsAndBlocksFromRegion(regionName: String, world: World, playerUUID: UUID) {
        val containerItems = collectItemsFromRegion(regionName, world)
        val playerBlocks = storePlayerBlocksFromRegion(regionName, world, playerUUID)

        if (containerItems.isNotEmpty() || playerBlocks.isNotEmpty()) {
            plugin.storageConfig.storeItems(playerUUID, regionName, containerItems, playerBlocks)

            val totalItems = containerItems.size + playerBlocks.size

            Bukkit.getPlayer(playerUUID)?.let { player ->
                if (player.isOnline) {
                    player.sendMessage("${ChatColor.GREEN}Stored ${containerItems.size} items and ${playerBlocks.size} blocks from $regionName")

                    if (totalItems > StorageGUISession.ITEMS_PER_PAGE) {
                        val pages = (totalItems - 1) / StorageGUISession.ITEMS_PER_PAGE + 1
                        player.sendMessage("${ChatColor.YELLOW}Your items are stored across $pages pages. Use /zrretrieve to access them.")
                    }
                }
            }

            plugin.logger.info("Stored ${containerItems.size} items and ${playerBlocks.size} blocks from region $regionName")
        }
    }

    /**
     * Public API: Stores player-placed blocks from a region.
     * Routes to async or sync implementation based on region size and config.
     */
    fun storePlayerBlocksFromRegion(regionName: String, world: World, playerUUID: UUID): List<ItemStack> {
        if (!plugin.configManager.isBlockStorage) {
            return emptyList()
        }

        // Check if async scanning is enabled
        if (!plugin.configManager.isAsyncScanningEnabled) {
            return storePlayerBlocksFromRegionSync(regionName, world, playerUUID)
        }

        // Check region size to decide path
        val scanRegion = asyncScanService.createScanRegion(regionName, world)
        if (scanRegion == null || scanRegion.chunkCount < plugin.configManager.minChunksForAsync) {
            return storePlayerBlocksFromRegionSync(regionName, world, playerUUID)
        }

        // Use async path with blocking bridge for larger regions
        return runBlocking {
            storePlayerBlocksFromRegionAsync(regionName, world, playerUUID)
        }
    }

    /**
     * Synchronous version of storePlayerBlocksFromRegion.
     * Used for small regions or as fallback.
     */
    private fun storePlayerBlocksFromRegionSync(regionName: String, world: World, playerUUID: UUID): List<ItemStack> {
        val playerBlocks = mutableListOf<ItemStack>()

        val compositeKey = "${world.name}:$regionName"
        val clipboard = plugin.worldEditManager.getClipboard(compositeKey)
        if (clipboard == null) {
            plugin.logger.warning("No schematic found for region $regionName in world ${world.name} - cannot compare blocks")
            return playerBlocks
        }

        val regionManager = WorldGuard.getInstance().platform
            .regionContainer.get(BukkitAdapter.adapt(world)) ?: return playerBlocks

        val region = regionManager.getRegion(regionName) ?: return playerBlocks

        val min = region.minimumPoint
        val max = region.maximumPoint
        val blockBlacklist = buildBlockBlacklist()

        var blocksCompared = 0
        var blocksStored = 0

        for (x in min.x()..max.x()) {
            for (y in min.y()..max.y()) {
                for (z in min.z()..max.z()) {
                    blocksCompared++

                    val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    val currentBlock = loc.getBlock()
                    val currentMaterial = currentBlock.type

                    if (currentMaterial in blockBlacklist) continue

                    val pos = BlockVector3.at(x, y, z)
                    val originalBlockState = clipboard.getBlock(pos)
                    val originalBlockType = originalBlockState.blockType.id
                    val originalMaterial = Material.matchMaterial(
                        originalBlockType.replace("minecraft:", "").uppercase()
                    )

                    if (originalMaterial == null || currentMaterial != originalMaterial) {
                        if (currentMaterial in CONTAINER_TYPES) continue

                        val blockItem = blockToItemStack(currentBlock)
                        if (blockItem != null) {
                            playerBlocks.add(blockItem)
                            blocksStored++
                        }
                    }
                }
            }
        }

        if (plugin.configManager.isDebug) {
            plugin.logger.info("Block comparison for $regionName: $blocksCompared blocks scanned, $blocksStored player-placed blocks stored")
        }

        if (playerBlocks.isNotEmpty()) {
            Bukkit.getPlayer(playerUUID)?.let { player ->
                if (player.isOnline) {
                    player.sendMessage("${ChatColor.GREEN}Stored $blocksStored player-placed blocks from $regionName")
                }
            }
        }

        return playerBlocks
    }

    private fun preserveContainerState(item: ItemStack, block: Block) {
        val container = block.state as? Container ?: return
        val itemMeta = item.itemMeta ?: return

        if (itemMeta is BlockStateMeta) {
            itemMeta.blockState = container
        }

        container.customName?.let { name ->
            itemMeta.setDisplayName(name)
        }

        item.itemMeta = itemMeta
    }

    private fun blockToItemStack(block: Block): ItemStack? {
        val type = block.type

        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return null
        }

        val item = ItemStack(type, 1)

        if (type in CONTAINER_TYPES) {
            preserveContainerState(item, block)
        }

        return item
    }

    private fun buildBlockBlacklist(): Set<Material> {
        val blacklist = DEFAULT_BLOCK_BLACKLIST.toMutableSet()

        plugin.configManager.blockBlacklist.forEach { materialName ->
            val material = Material.matchMaterial(materialName)
            if (material != null) {
                blacklist.add(material)
            } else {
                plugin.logger.warning("Invalid material in block blacklist: $materialName")
            }
        }

        return blacklist
    }

    fun openRetrievalGUI(player: Player) {
        val items = plugin.storageConfig.getAllStoredItems(player.uniqueId)

        if (items.isEmpty()) {
            player.sendMessage(plugin.configManager.getMessage("no-stored-items"))
            return
        }

        val session = StorageGUISession.create(player.uniqueId, items)
        activeGUISessions[player.uniqueId] = session

        openGUIPage(player, session, 0)
    }

    private fun openGUIPage(player: Player, session: StorageGUISession, page: Int) {
        var currentPage = page
        val totalPages = session.totalPages

        if (currentPage < 0) currentPage = 0
        if (currentPage >= totalPages) currentPage = totalPages - 1

        session.currentPage = currentPage

        val title = if (totalPages > 1) {
            "Retrieved Items - Page ${currentPage + 1}/$totalPages"
        } else {
            "Retrieve Your Items"
        }

        val gui = Bukkit.createInventory(StorageGUIHolder(player.uniqueId), 54, title)

        synchronized(session.items) {
            val startIndex = currentPage * StorageGUISession.ITEMS_PER_PAGE
            val endIndex = minOf(startIndex + StorageGUISession.ITEMS_PER_PAGE, session.items.size)

            if (startIndex >= session.items.size) {
                plugin.logger.warning("Page index out of bounds for player ${player.name} (startIndex: $startIndex, items: ${session.items.size})")
                return
            }

            for (i in startIndex until endIndex) {
                session.items[i]?.let { gui.addItem(it) }
            }

            // Always show page indicator and close button
            gui.setItem(49, createNavigationButton(
                Material.PAPER,
                "${ChatColor.YELLOW}Page ${currentPage + 1} of $totalPages",
                "${ChatColor.GRAY}Total items: ${session.items.size}"
            ))

            gui.setItem(50, createNavigationButton(
                Material.BARRIER,
                "${ChatColor.RED}Close",
                "${ChatColor.GRAY}Click to close and save items"
            ))

            // Only show navigation arrows if multiple pages exist
            if (totalPages > 1) {
                if (currentPage > 0) {
                    gui.setItem(45, createNavigationButton(
                        Material.ARROW,
                        "${ChatColor.GREEN}« Previous Page",
                        "${ChatColor.GRAY}Click to go to page $currentPage"
                    ))
                }

                if (currentPage < totalPages - 1) {
                    gui.setItem(53, createNavigationButton(
                        Material.ARROW,
                        "${ChatColor.GREEN}Next Page »",
                        "${ChatColor.GRAY}Click to go to page ${currentPage + 2}"
                    ))
                }
            }
        }

        // Set transitioning flag to prevent onInventoryClose from removing session
        session.isTransitioning = true
        player.openInventory(gui)
        // Re-add session in case close handler ran during transition
        activeGUISessions[player.uniqueId] = session
        session.isTransitioning = false
    }

    private fun createNavigationButton(material: Material, name: String, lore: String): ItemStack {
        val button = ItemStack(material)
        val meta = button.itemMeta
        meta?.setDisplayName(name)
        meta?.lore = listOf(lore)
        button.itemMeta = meta
        return button
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is StorageGUIHolder) return

        val player = event.whoClicked as? Player ?: return
        val session = activeGUISessions[player.uniqueId] ?: return

        val slot = event.slot
        val clickType = event.click

        // Block shift-click from player inventory to GUI
        if (event.clickedInventory != event.view.topInventory) {
            if (clickType.isShiftClick) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot add items to the retrieval GUI!")
                return
            }
        }

        // Handle navigation buttons
        if (slot in 45..53) {
            event.isCancelled = true

            when {
                slot == 45 && session.currentPage > 0 -> {
                    // Sync current page state before navigating away
                    syncCurrentPageToSession(session, event.inventory)
                    openGUIPage(player, session, session.currentPage - 1)
                }
                slot == 53 && session.currentPage < session.totalPages - 1 -> {
                    // Sync current page state before navigating away
                    syncCurrentPageToSession(session, event.inventory)
                    openGUIPage(player, session, session.currentPage + 1)
                }
                slot == 50 -> player.closeInventory()
            }
            return
        }

        // Block placing items into GUI item slots
        if (event.clickedInventory == event.view.topInventory && slot in 0 until 45) {
            if (clickType == ClickType.NUMBER_KEY) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot add items to the retrieval GUI!")
                return
            }

            val cursor = event.cursor
            if (cursor != null && cursor.type != Material.AIR) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot add items to the retrieval GUI!")
                return
            }
        }
    }

    /**
     * Synchronizes the current GUI page state back to the session's items list.
     * This captures which items have been taken from the current page.
     */
    private fun syncCurrentPageToSession(session: StorageGUISession, inventory: Inventory) {
        synchronized(session.items) {
            val startIndex = session.currentPage * StorageGUISession.ITEMS_PER_PAGE
            val endIndex = minOf(startIndex + StorageGUISession.ITEMS_PER_PAGE, session.items.size)

            // Remove old items for this page range (reverse order to avoid index shift issues)
            for (i in (endIndex - 1) downTo startIndex) {
                if (i < session.items.size) {
                    session.items.removeAt(i)
                }
            }

            // Insert current GUI items at startIndex (only non-null/non-air items)
            var insertIndex = startIndex
            for (slot in 0 until StorageGUISession.ITEMS_PER_PAGE) {
                val item = inventory.getItem(slot)
                if (item != null && item.type != Material.AIR) {
                    session.items.add(insertIndex, item.clone())
                    insertIndex++
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is StorageGUIHolder) return

        val player = event.player as? Player ?: return
        val playerUUID = player.uniqueId
        val closingInventory = event.inventory

        val session = activeGUISessions[playerUUID] ?: return

        // Don't process close during page transitions
        if (session.isTransitioning) return

        // Now safe to remove and process the close
        activeGUISessions.remove(playerUUID)

        // Sync the current page state to session before calculating remaining items
        syncCurrentPageToSession(session, closingInventory)

        // Get all remaining items from the session (all pages, now properly synced)
        val remainingItems = synchronized(session.items) {
            session.items.filter { it.type != Material.AIR }.map { it.clone() }
        }

        if (remainingItems.isEmpty()) {
            plugin.storageConfig.clearPlayerStorage(playerUUID)
            player.sendMessage(plugin.configManager.getMessage("items-retrieved"))
        } else {
            plugin.storageConfig.updatePartialStorage(playerUUID, remainingItems)

            // Use originalItemCount for accurate "items taken" count
            val itemsTaken = session.originalItemCount - remainingItems.size
            player.sendMessage("${ChatColor.YELLOW}Retrieved $itemsTaken items. ${remainingItems.size} items remain in storage. Use /zrretrieve to get them.")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUUID = event.player.uniqueId
        val session = activeGUISessions.remove(playerUUID)

        if (session != null && plugin.configManager.isDebug) {
            plugin.logger.info("Cleaned up orphaned GUI session for ${event.player.name}")
        }
    }

    val activeSessionCount: Int
        get() = activeGUISessions.size

    private fun findWorldForRegion(regionName: String): World? {
        for (world in Bukkit.getWorlds()) {
            val regionManager = WorldGuard.getInstance().platform
                .regionContainer.get(BukkitAdapter.adapt(world))

            if (regionManager?.hasRegion(regionName) == true) {
                return world
            }
        }
        return Bukkit.getWorlds().firstOrNull()
    }

    /**
     * Custom InventoryHolder used only as a marker/identifier for StorageManager GUI inventories.
     *
     * This holder is not a functional InventoryHolder implementation and its [getInventory] method
     * must never be called directly. The Bukkit API requires an InventoryHolder implementation to
     * provide this method with a non-null Inventory return type, but for this marker holder
     * we intentionally do not maintain a backing inventory instance.
     *
     * The WRONG_NULLABILITY_FOR_JAVA_OVERRIDE suppression is used because the Java
     * InventoryHolder interface requires this method, but this implementation deliberately
     * never returns an Inventory and always throws instead.
     */
    private class StorageGUIHolder(private val playerUUID: UUID) : InventoryHolder {
        @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
        override fun getInventory(): Inventory = throw UnsupportedOperationException("Not used")

        fun getPlayerUUID(): UUID = playerUUID
    }
}
