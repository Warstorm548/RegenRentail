package com.zonerental.async

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.zonerental.ZoneRental
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import java.util.EnumSet

/**
 * Service for async chunk-based region scanning using ChunkSnapshot pre-filtering.
 *
 * Uses a two-phase approach:
 * - Phase A: Scan ChunkSnapshots for block types (fast, mostly async)
 * - Phase B: Access actual blocks only for found containers (main thread, minimal)
 */
class AsyncScanService(private val plugin: ZoneRental) {

    val tpsMonitor = TpsMonitor(plugin)

    companion object {
        /** Container block types that need inventory scanning */
        val CONTAINER_TYPES: Set<Material> = EnumSet.of(
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

        /** Check if material is a shulker box (stores items when broken) */
        fun isShulkerBox(material: Material): Boolean {
            return material.name.endsWith("SHULKER_BOX")
        }
    }

    /**
     * Creates a ScanRegion from a WorldGuard region with Y-level bounds.
     */
    fun createScanRegion(regionName: String, world: World): ScanRegion? {
        val regionManager = WorldGuard.getInstance().platform.regionContainer
            .get(BukkitAdapter.adapt(world)) ?: return null

        val wgRegion = regionManager.getRegion(regionName) ?: return null
        val min = wgRegion.minimumPoint
        val max = wgRegion.maximumPoint

        return ScanRegion(
            regionName = regionName,
            worldName = world.name,
            minX = min.x(),
            maxX = max.x(),
            minY = min.y(),
            maxY = max.y(),
            minZ = min.z(),
            maxZ = max.z()
        )
    }

    /**
     * Determines the appropriate scan strategy based on region size.
     */
    fun determineStrategy(scanRegion: ScanRegion): ScanStrategy {
        return ScanStrategy.fromChunkCount(scanRegion.chunkCount)
    }

    /**
     * Calculates chunk batches for a region.
     */
    fun calculateChunkBatches(scanRegion: ScanRegion, strategy: ScanStrategy): List<ChunkBatch> {
        val chunks = mutableListOf<ChunkCoord>()

        val minChunkX = scanRegion.minX shr 4
        val maxChunkX = scanRegion.maxX shr 4
        val minChunkZ = scanRegion.minZ shr 4
        val maxChunkZ = scanRegion.maxZ shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                chunks.add(ChunkCoord(
                    chunkX = cx,
                    chunkZ = cz,
                    minY = scanRegion.minY,
                    maxY = scanRegion.maxY
                ))
            }
        }

        return chunks.chunked(strategy.batchSize).mapIndexed { index, batch ->
            ChunkBatch(
                chunks = batch,
                batchIndex = index,
                totalBatches = (chunks.size + strategy.batchSize - 1) / strategy.batchSize
            )
        }
    }

    /**
     * Checks if a region exceeds the maximum allowed chunk count.
     */
    fun isRegionTooLarge(scanRegion: ScanRegion): Boolean {
        return scanRegion.chunkCount > plugin.configManager.maxRentalChunks
    }

    /**
     * Gets a ChunkSnapshot, loading the chunk if necessary.
     * Must be called from main thread or within minecraftDispatcher context.
     */
    suspend fun getChunkSnapshot(world: World, chunkX: Int, chunkZ: Int): ChunkSnapshot {
        return withContext(plugin.minecraftDispatcher) {
            val chunk = world.getChunkAt(chunkX, chunkZ)
            if (!chunk.isLoaded) {
                chunk.load(false) // Don't generate if doesn't exist
            }
            chunk.getChunkSnapshot()
        }
    }

    /**
     * Scans a ChunkSnapshot for container block types within region bounds.
     * This can run off the main thread after snapshot is obtained.
     */
    fun scanSnapshotForContainers(
        snapshot: ChunkSnapshot,
        chunkCoord: ChunkCoord,
        scanRegion: ScanRegion
    ): List<BlockCoord> {
        val containerLocations = mutableListOf<BlockCoord>()

        // Calculate block bounds within this chunk, constrained by region
        val chunkBlockMinX = chunkCoord.chunkX shl 4
        val chunkBlockMaxX = chunkBlockMinX + 15

        val chunkBlockMinZ = chunkCoord.chunkZ shl 4
        val chunkBlockMaxZ = chunkBlockMinZ + 15

        val blockMinX = maxOf(chunkBlockMinX, scanRegion.minX)
        val blockMaxX = minOf(chunkBlockMaxX, scanRegion.maxX)
        val blockMinZ = maxOf(chunkBlockMinZ, scanRegion.minZ)
        val blockMaxZ = minOf(chunkBlockMaxZ, scanRegion.maxZ)

        // Y-level aware scanning
        for (x in blockMinX..blockMaxX) {
            for (y in chunkCoord.minY..chunkCoord.maxY) {
                for (z in blockMinZ..blockMaxZ) {
                    val blockType = snapshot.getBlockType(x and 0xF, y, z and 0xF)
                    if (blockType in CONTAINER_TYPES) {
                        containerLocations.add(BlockCoord(x, y, z))
                    }
                }
            }
        }

        return containerLocations
    }

    /**
     * Scans a ChunkSnapshot for blocks that differ from the original clipboard.
     * Used for detecting player-placed blocks.
     */
    fun scanSnapshotForChangedBlocks(
        snapshot: ChunkSnapshot,
        chunkCoord: ChunkCoord,
        scanRegion: ScanRegion,
        clipboard: Clipboard,
        blacklist: Set<Material>
    ): List<BlockCoord> {
        val changedBlocks = mutableListOf<BlockCoord>()

        val chunkBlockMinX = chunkCoord.chunkX shl 4
        val chunkBlockMaxX = chunkBlockMinX + 15
        val chunkBlockMinZ = chunkCoord.chunkZ shl 4
        val chunkBlockMaxZ = chunkBlockMinZ + 15

        val blockMinX = maxOf(chunkBlockMinX, scanRegion.minX)
        val blockMaxX = minOf(chunkBlockMaxX, scanRegion.maxX)
        val blockMinZ = maxOf(chunkBlockMinZ, scanRegion.minZ)
        val blockMaxZ = minOf(chunkBlockMaxZ, scanRegion.maxZ)

        for (x in blockMinX..blockMaxX) {
            for (y in chunkCoord.minY..chunkCoord.maxY) {
                for (z in blockMinZ..blockMaxZ) {
                    val currentType = snapshot.getBlockType(x and 0xF, y, z and 0xF)

                    // Skip blacklisted materials (dirt, stone, air, etc.)
                    if (currentType in blacklist) continue
                    // Skip containers (handled separately)
                    if (currentType in CONTAINER_TYPES) continue

                    // Compare to clipboard (with bounds checking)
                    val pos = BlockVector3.at(x, y, z)
                    try {
                        val originalBlockState = clipboard.getBlock(pos)
                        val originalBlockType = originalBlockState.blockType.id
                        val originalMaterial = Material.matchMaterial(
                            originalBlockType.replace("minecraft:", "").uppercase()
                        )

                        if (originalMaterial == null || currentType != originalMaterial) {
                            changedBlocks.add(BlockCoord(x, y, z))
                        }
                    } catch (e: Exception) {
                        // Block is outside clipboard bounds (region may have been resized)
                        // Treat as changed block since we can't compare
                        changedBlocks.add(BlockCoord(x, y, z))
                    }
                }
            }
        }

        return changedBlocks
    }

    /**
     * Logs scan progress for debugging.
     */
    fun logScanProgress(
        regionName: String,
        batchIndex: Int,
        totalBatches: Int,
        containersFound: Int
    ) {
        if (plugin.configManager.isDebugAsync) {
            plugin.logger.info(
                "[$regionName] Scanning batch ${batchIndex + 1}/$totalBatches, containers found: $containersFound"
            )
        }
    }

    /**
     * Logs scan completion.
     */
    fun logScanComplete(
        regionName: String,
        totalContainers: Int,
        totalBlocks: Long,
        scanTimeMs: Long
    ) {
        if (plugin.configManager.isDebugAsync) {
            plugin.logger.info(
                "[$regionName] Scan complete: $totalContainers containers, $totalBlocks blocks scanned in ${scanTimeMs}ms"
            )
        }
    }

    /**
     * Logs region size warning for large regions.
     */
    fun logRegionSizeWarning(scanRegion: ScanRegion) {
        val chunkCount = scanRegion.chunkCount
        when {
            chunkCount > 2000 -> {
                plugin.logger.warning(
                    "[${scanRegion.regionName}] Very large region: $chunkCount chunks. " +
                    "Using very conservative strategy. Estimated time: ${tpsMonitor.estimateScanTime(chunkCount) / 1000}s"
                )
            }
            chunkCount > 1000 -> {
                plugin.logger.info(
                    "[${scanRegion.regionName}] Large region: $chunkCount chunks. " +
                    "Estimated time: ${tpsMonitor.estimateScanTime(chunkCount) / 1000}s"
                )
            }
        }
    }

}
