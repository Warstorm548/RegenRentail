package com.regionrental.managers

import com.regionrental.RegionRental
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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
import java.util.EnumSet
import java.util.UUID

/**
 * Manages item storage from expired rentals and retrieval GUI.
 */
class StorageManager(private val plugin: RegionRental) : Listener {

    private val activeGUISessions = mutableMapOf<UUID, StorageGUISession>()

    companion object {
        private const val ITEMS_PER_PAGE = 45

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

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Scans and collects items from containers in a region.
     * Also clears the containers.
     */
    fun collectItemsFromRegion(regionName: String, world: World): List<ItemStack> {
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
                        if (isShulkerBox(block.type)) continue

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

                    if (allItems.size > ITEMS_PER_PAGE) {
                        val pages = (allItems.size - 1) / ITEMS_PER_PAGE + 1
                        player.sendMessage("${ChatColor.YELLOW}Your items are stored across $pages pages. Use /rrretrieve to access them.")
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

                    if (totalItems > ITEMS_PER_PAGE) {
                        val pages = (totalItems - 1) / ITEMS_PER_PAGE + 1
                        player.sendMessage("${ChatColor.YELLOW}Your items are stored across $pages pages. Use /rrretrieve to access them.")
                    }
                }
            }

            plugin.logger.info("Stored ${containerItems.size} items and ${playerBlocks.size} blocks from region $regionName")
        }
    }

    /**
     * Stores player-placed blocks from a region by comparing to original schematic.
     */
    fun storePlayerBlocksFromRegion(regionName: String, world: World, playerUUID: UUID): List<ItemStack> {
        val playerBlocks = mutableListOf<ItemStack>()

        if (!plugin.configManager.isBlockStorage) {
            return playerBlocks
        }

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

    private fun isShulkerBox(material: Material): Boolean = material.name.endsWith("SHULKER_BOX")

    fun openRetrievalGUI(player: Player) {
        val items = plugin.storageConfig.getAllStoredItems(player.uniqueId)

        if (items.isEmpty()) {
            player.sendMessage(plugin.configManager.getMessage("no-stored-items"))
            return
        }

        val session = StorageGUISession(player.uniqueId, items)
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
            val startIndex = currentPage * ITEMS_PER_PAGE
            val endIndex = minOf(startIndex + ITEMS_PER_PAGE, session.items.size)

            if (startIndex >= session.items.size) {
                plugin.logger.warning("Page index out of bounds for player ${player.name} (startIndex: $startIndex, items: ${session.items.size})")
                return
            }

            for (i in startIndex until endIndex) {
                session.items[i]?.let { gui.addItem(it) }
            }

            if (totalPages > 1) {
                if (currentPage > 0) {
                    gui.setItem(45, createNavigationButton(
                        Material.ARROW,
                        "${ChatColor.GREEN}« Previous Page",
                        "${ChatColor.GRAY}Click to go to page $currentPage"
                    ))
                }

                gui.setItem(49, createNavigationButton(
                    Material.PAPER,
                    "${ChatColor.YELLOW}Page ${currentPage + 1} of $totalPages",
                    "${ChatColor.GRAY}Total items: ${session.items.size}"
                ))

                if (currentPage < totalPages - 1) {
                    gui.setItem(53, createNavigationButton(
                        Material.ARROW,
                        "${ChatColor.GREEN}Next Page »",
                        "${ChatColor.GRAY}Click to go to page ${currentPage + 2}"
                    ))
                }

                gui.setItem(50, createNavigationButton(
                    Material.BARRIER,
                    "${ChatColor.RED}Close",
                    "${ChatColor.GRAY}Click to close and save items"
                ))
            }
        }

        player.openInventory(gui)
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
                slot == 45 && session.currentPage > 0 -> openGUIPage(player, session, session.currentPage - 1)
                slot == 53 && session.currentPage < session.totalPages - 1 -> openGUIPage(player, session, session.currentPage + 1)
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

    private fun getRemainingItemsFromGUI(inventory: Inventory): List<ItemStack> {
        val remaining = mutableListOf<ItemStack>()
        for (i in 0 until 45) {
            val item = inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                remaining.add(item.clone())
            }
        }
        return remaining
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is StorageGUIHolder) return

        val player = event.player as? Player ?: return
        val playerUUID = player.uniqueId
        val closingInventory = event.inventory

        val session = activeGUISessions.remove(playerUUID) ?: return

        val remainingItems = getRemainingItemsFromGUI(closingInventory)

        if (remainingItems.isEmpty()) {
            plugin.storageConfig.clearPlayerStorage(playerUUID)
            player.sendMessage(plugin.configManager.getMessage("items-retrieved"))
        } else {
            plugin.storageConfig.updatePartialStorage(playerUUID, remainingItems)

            val itemsTaken = synchronized(session.items) {
                session.items.size - remainingItems.size
            }
            player.sendMessage("${ChatColor.YELLOW}Retrieved $itemsTaken items. ${remainingItems.size} items remain in storage. Use /rrretrieve to get them.")
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

    // Inner class for GUI session tracking
    private class StorageGUISession(
        val playerUUID: UUID,
        val items: List<ItemStack>
    ) {
        var currentPage: Int = 0

        val totalPages: Int
            get() = maxOf(1, (items.size - 1) / ITEMS_PER_PAGE + 1)
    }

    // Custom InventoryHolder for identification
    private class StorageGUIHolder(private val playerUUID: UUID) : InventoryHolder {
        override fun getInventory(): Inventory? = null

        fun getPlayerUUID(): UUID = playerUUID
    }
}
