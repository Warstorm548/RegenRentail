package com.zonerental.managers

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.zonerental.ZoneRental
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldguard.WorldGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.World
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Manages WorldEdit integration for block restoration.
 * Captures region state when rental starts and restores it when rental expires.
 * Uses Sponge Schematic format (.schem) for reliable cross-session persistence.
 */
class WorldEditManager(private val plugin: ZoneRental) {

    private val schematicsFolder = File(plugin.dataFolder, "schematics")

    // LRU cache for clipboards - limits memory usage
    private val maxCacheSize: Int
    private val clipboardCache: MutableMap<String, Clipboard>

    // Track which schematics exist on disk (for hasCapture checks without loading)
    private val knownSchematics: MutableSet<String> = ConcurrentHashMap.newKeySet()

    companion object {
        private const val DEFAULT_CACHE_SIZE = 20
        private const val SCHEMATIC_EXTENSION = ".schem"
        private const val OLD_EXTENSION = ".dat"
    }

    init {
        maxCacheSize = plugin.configManager.getSchematicCacheSize(DEFAULT_CACHE_SIZE)

        // Create LRU cache with access-order ordering, wrapped for thread safety
        clipboardCache = Collections.synchronizedMap(object : LinkedHashMap<String, Clipboard>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Clipboard>): Boolean {
                val shouldRemove = size > maxCacheSize
                if (shouldRemove && plugin.configManager.isDebug) {
                    plugin.logger.info("Evicting schematic from cache: ${eldest.key}")
                }
                return shouldRemove
            }
        })

        // Create schematics folder if it doesn't exist
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs()
        }

        // Index existing schematics (don't load them into memory)
        indexSchematics()
    }

    /**
     * Captures the current state of a WorldGuard region.
     */
    fun captureRegion(regionName: String, world: World): Boolean {
        if (!plugin.configManager.isBlockRestoration) {
            return false
        }

        return try {
            val wgRegion = getWorldGuardRegion(regionName, world)
            if (wgRegion == null) {
                plugin.logger.warning("Could not find WorldGuard region: $regionName in world ${world.name}")
                return false
            }

            val weWorld = BukkitAdapter.adapt(world)
            val min = wgRegion.minimumPoint
            val max = wgRegion.maximumPoint
            val region = CuboidRegion(weWorld, min, max)

            val clipboard = BlockArrayClipboard(region)

            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val copy = ForwardExtentCopy(editSession, region, clipboard, region.minimumPoint)
                copy.isCopyingEntities = true
                copy.isCopyingBiomes = false
                Operations.complete(copy)
            }

            val compositeKey = "${world.name}:$regionName"
            clipboardCache[compositeKey] = clipboard
            knownSchematics.add(compositeKey)

            saveSchematic(compositeKey, clipboard)

            if (plugin.configManager.isDebug) {
                plugin.logger.info("Captured region state for: $regionName in world ${world.name}")
            }

            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to capture region: $regionName in world ${world.name}", e)
            false
        }
    }

    /**
     * Restores a region to its captured state.
     */
    fun restoreRegion(regionName: String, world: World): Boolean {
        if (!plugin.configManager.isBlockRestoration) {
            return false
        }

        val compositeKey = "${world.name}:$regionName"
        val clipboard = getOrLoadClipboard(compositeKey)

        if (clipboard == null) {
            plugin.logger.warning("No saved state found for region: $regionName in world ${world.name}")
            return false
        }

        return try {
            val weWorld = BukkitAdapter.adapt(world)

            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val holder = ClipboardHolder(clipboard)
                val operation = holder.createPaste(editSession)
                    .to(clipboard.origin)
                    .ignoreAirBlocks(false)
                    .build()
                Operations.complete(operation)
            }

            if (plugin.configManager.isDebug) {
                plugin.logger.info("Restored region state for: $regionName in world ${world.name}")
            }

            if (plugin.configManager.isAutoDeleteSchematics) {
                clipboardCache.remove(compositeKey)
                knownSchematics.remove(compositeKey)
                deleteSchematic(compositeKey)
            }

            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to restore region: $regionName in world ${world.name}", e)
            false
        }
    }

    /**
     * Checks if a region state has been captured (checks index, doesn't load from disk).
     * World-aware version using composite key format.
     */
    fun hasCapture(regionName: String, world: World): Boolean {
        val compositeKey = "${world.name}:$regionName"
        return compositeKey in knownSchematics
    }

    /**
     * Gets the captured clipboard for a region (lazy loads from disk if needed).
     */
    fun getClipboard(regionName: String): Clipboard? = getOrLoadClipboard(regionName)

    /**
     * Deletes a captured region state from cache, index, and disk.
     * World-aware version using composite key format.
     */
    fun deleteCapture(regionName: String, world: World) {
        val compositeKey = "${world.name}:$regionName"
        clipboardCache.remove(compositeKey)
        knownSchematics.remove(compositeKey)
        deleteSchematic(compositeKey)
    }

    /**
     * Gets the current cache size (for monitoring/debugging).
     */
    val cacheSize: Int
        get() = clipboardCache.size

    /**
     * Gets the total number of known schematics on disk.
     */
    val totalSchematicCount: Int
        get() = knownSchematics.size

    /**
     * Clears the memory cache (schematics remain on disk).
     */
    fun clearCache() {
        clipboardCache.clear()
        if (plugin.configManager.isDebug) {
            plugin.logger.info("Schematic cache cleared")
        }
    }

    // ========================================
    // ASYNC METHODS (File I/O on background thread)
    // ========================================

    /**
     * Async version of captureRegion.
     * WorldEdit clipboard creation runs on main thread, file I/O runs on IO dispatcher.
     */
    suspend fun captureRegionAsync(regionName: String, world: World): Boolean {
        if (!plugin.configManager.isBlockRestoration) {
            return false
        }

        return try {
            // WorldEdit operations must run on main thread
            val clipboard = withContext(plugin.minecraftDispatcher) {
                createClipboard(regionName, world)
            } ?: return false

            val compositeKey = "${world.name}:$regionName"
            clipboardCache[compositeKey] = clipboard
            knownSchematics.add(compositeKey)

            // File I/O on background thread
            withContext(Dispatchers.IO) {
                saveSchematic(compositeKey, clipboard)
            }

            if (plugin.configManager.isDebug) {
                plugin.logger.info("Captured region state for: $regionName in world ${world.name}")
            }

            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to capture region: $regionName in world ${world.name}", e)
            false
        }
    }

    /**
     * Async version of restoreRegion.
     * WorldEdit paste runs on main thread, file I/O runs on IO dispatcher.
     */
    suspend fun restoreRegionAsync(regionName: String, world: World): Boolean {
        if (!plugin.configManager.isBlockRestoration) {
            return false
        }

        val compositeKey = "${world.name}:$regionName"

        // Load schematic from disk on IO thread if not in cache
        val clipboard = clipboardCache[compositeKey] ?: withContext(Dispatchers.IO) {
            loadSchematic(compositeKey)
        }

        if (clipboard == null) {
            plugin.logger.warning("No saved state found for region: $regionName in world ${world.name}")
            return false
        }

        return try {
            // WorldEdit paste operation on main thread
            withContext(plugin.minecraftDispatcher) {
                performRestore(clipboard, world)
            }

            if (plugin.configManager.isDebug) {
                plugin.logger.info("Restored region state for: $regionName in world ${world.name}")
            }

            if (plugin.configManager.isAutoDeleteSchematics) {
                clipboardCache.remove(compositeKey)
                knownSchematics.remove(compositeKey)
                withContext(Dispatchers.IO) {
                    deleteSchematic(compositeKey)
                }
            }

            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to restore region: $regionName in world ${world.name}", e)
            false
        }
    }

    /**
     * Creates a WorldEdit clipboard for a region.
     * Must be called from main thread.
     */
    private fun createClipboard(regionName: String, world: World): Clipboard? {
        val wgRegion = getWorldGuardRegion(regionName, world)
        if (wgRegion == null) {
            plugin.logger.warning("Could not find WorldGuard region: $regionName in world ${world.name}")
            return null
        }

        val weWorld = BukkitAdapter.adapt(world)
        val min = wgRegion.minimumPoint
        val max = wgRegion.maximumPoint
        val region = CuboidRegion(weWorld, min, max)

        val clipboard = BlockArrayClipboard(region)

        WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
            val copy = ForwardExtentCopy(editSession, region, clipboard, region.minimumPoint)
            copy.isCopyingEntities = true
            copy.isCopyingBiomes = false
            Operations.complete(copy)
        }

        return clipboard
    }

    /**
     * Performs WorldEdit paste operation.
     * Must be called from main thread.
     */
    private fun performRestore(clipboard: Clipboard, world: World) {
        val weWorld = BukkitAdapter.adapt(world)

        WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
            val holder = ClipboardHolder(clipboard)
            val operation = holder.createPaste(editSession)
                .to(clipboard.origin)
                .ignoreAirBlocks(false)
                .build()
            Operations.complete(operation)
        }
    }

    // ========================================
    // SYNC METHODS (Original implementation)
    // ========================================

    private fun getOrLoadClipboard(compositeKey: String): Clipboard? {
        // Check cache first
        clipboardCache[compositeKey]?.let { return it }

        // Not in cache - check if it exists on disk
        if (compositeKey !in knownSchematics) {
            return null
        }

        // Load from disk
        val loaded = loadSchematic(compositeKey)
        if (loaded != null) {
            clipboardCache[compositeKey] = loaded
            if (plugin.configManager.isDebug) {
                plugin.logger.info("Lazy loaded schematic from disk: $compositeKey")
            }
        }
        return loaded
    }

    private fun getWorldGuardRegion(regionName: String, world: World) =
        WorldGuard.getInstance().platform.regionContainer
            .get(BukkitAdapter.adapt(world))
            ?.getRegion(regionName)

    private fun saveSchematic(regionName: String, clipboard: Clipboard) {
        val file = File(schematicsFolder, "$regionName$SCHEMATIC_EXTENSION")

        runCatching {
            FileOutputStream(file).use { fos ->
                BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(fos).use { writer ->
                    writer.write(clipboard)
                }
            }
            if (plugin.configManager.isDebug) {
                plugin.logger.info("Saved schematic: $regionName$SCHEMATIC_EXTENSION")
            }
        }.onFailure { e ->
            plugin.logger.log(Level.WARNING, "Failed to save schematic for: $regionName", e)
        }
    }

    private fun indexSchematics() {
        if (!schematicsFolder.exists()) return

        // Index new .schem files
        schematicsFolder.listFiles { _, name -> name.endsWith(SCHEMATIC_EXTENSION) }?.forEach { file ->
            val regionName = file.name.removeSuffix(SCHEMATIC_EXTENSION)
            knownSchematics.add(regionName)
        }

        // Migration: Handle legacy .dat files
        val datFiles = schematicsFolder.listFiles { _, name -> name.endsWith(OLD_EXTENSION) }
        if (datFiles != null && datFiles.isNotEmpty()) {
            plugin.logger.warning("Found ${datFiles.size} legacy .dat file(s) - these are EMPTY due to serialization bug in v2.5.1")
            if (plugin.configManager.isAutoDeleteSchematics) {
                var deleted = 0
                for (datFile in datFiles) {
                    if (datFile.delete()) deleted++
                }
                plugin.logger.info("Deleted $deleted empty legacy .dat file(s)")
                if (deleted < datFiles.size) {
                    plugin.logger.warning("Could not delete ${datFiles.size - deleted} legacy file(s) - check file permissions")
                }
            } else {
                plugin.logger.info("Set 'restoration.auto-delete-schematics: true' to auto-clean legacy files")
            }
        }

        if (knownSchematics.isNotEmpty()) {
            plugin.logger.info("Indexed ${knownSchematics.size} region schematic(s) (lazy loading enabled)")
        }
    }

    private fun loadSchematic(regionName: String): Clipboard? {
        val schemFile = File(schematicsFolder, "$regionName$SCHEMATIC_EXTENSION")

        if (schemFile.exists()) {
            return runCatching {
                val format = ClipboardFormats.findByFile(schemFile)
                if (format == null) {
                    plugin.logger.warning("Unknown schematic format: ${schemFile.name}")
                    return null
                }

                FileInputStream(schemFile).use { fis ->
                    format.getReader(fis).use { reader ->
                        val clipboard = reader.read()
                        if (plugin.configManager.isDebug) {
                            plugin.logger.info("Loaded schematic: $regionName$SCHEMATIC_EXTENSION")
                        }
                        clipboard
                    }
                }
            }.onFailure { e ->
                plugin.logger.log(Level.WARNING, "Failed to load schematic: ${schemFile.name}", e)
            }.getOrNull()
        }

        // Migration: Warn about legacy .dat files
        val oldFile = File(schematicsFolder, "$regionName$OLD_EXTENSION")
        if (oldFile.exists()) {
            plugin.logger.warning("Legacy .dat schematic for '$regionName' is empty (serialization bug). Will be re-captured on next rental.")
            if (plugin.configManager.isAutoDeleteSchematics) {
                if (!oldFile.delete()) {
                    plugin.logger.warning("Could not delete legacy schematic: ${oldFile.name}")
                }
            }
        }

        return null
    }

    private fun deleteSchematic(regionName: String) {
        // Delete new .schem file
        val schemFile = File(schematicsFolder, "$regionName$SCHEMATIC_EXTENSION")
        if (schemFile.exists()) {
            if (!schemFile.delete()) {
                plugin.logger.warning("Could not delete schematic: ${schemFile.name}")
            } else if (plugin.configManager.isDebug) {
                plugin.logger.info("Deleted schematic: ${schemFile.name}")
            }
        }

        // Also delete legacy .dat file if it exists
        val oldFile = File(schematicsFolder, "$regionName$OLD_EXTENSION")
        if (oldFile.exists()) {
            if (!oldFile.delete()) {
                plugin.logger.warning("Could not delete legacy schematic: ${oldFile.name}")
            } else if (plugin.configManager.isDebug) {
                plugin.logger.info("Deleted legacy schematic: ${oldFile.name}")
            }
        }
    }
}
