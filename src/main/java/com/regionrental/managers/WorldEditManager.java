package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages WorldEdit integration for block restoration
 * Captures region state when rental starts and restores it when rental expires
 * Uses Sponge Schematic format (.schem) for reliable cross-session persistence
 */
public class WorldEditManager {

    private final RegionRental plugin;
    private final File schematicsFolder;

    // LRU cache for clipboards - limits memory usage
    private static final int DEFAULT_CACHE_SIZE = 20;
    private final int maxCacheSize;
    private final Map<String, Clipboard> clipboardCache;

    // Track which schematics exist on disk (for hasCapture checks without loading)
    // Using thread-safe set for potential concurrent access
    private final Set<String> knownSchematics = ConcurrentHashMap.newKeySet();

    // File extension constants for Sponge schematic format
    private static final String SCHEMATIC_EXTENSION = ".schem";
    private static final String OLD_EXTENSION = ".dat";

    public WorldEditManager(RegionRental plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        this.maxCacheSize = plugin.getConfigManager().getSchematicCacheSize(DEFAULT_CACHE_SIZE);

        // Create LRU cache with access-order ordering, wrapped for thread safety
        this.clipboardCache = Collections.synchronizedMap(new LinkedHashMap<String, Clipboard>(maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Clipboard> eldest) {
                boolean shouldRemove = size() > maxCacheSize;
                if (shouldRemove && plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Evicting schematic from cache: " + eldest.getKey());
                }
                return shouldRemove;
            }
        });

        // Create schematics folder if it doesn't exist
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        // Index existing schematics (don't load them into memory)
        indexSchematics();
    }

    /**
     * Captures the current state of a WorldGuard region
     * @param regionName The WorldGuard region name
     * @param world The world the region is in
     * @return true if capture was successful
     */
    public boolean captureRegion(String regionName, World world) {
        if (!plugin.getConfigManager().isBlockRestoration()) {
            return false; // Block restoration disabled
        }

        try {
            // Get the WorldGuard region
            ProtectedRegion wgRegion = getWorldGuardRegion(regionName, world);
            if (wgRegion == null) {
                plugin.getLogger().warning("Could not find WorldGuard region: " + regionName + " in world " + world.getName());
                return false;
            }

            // Convert Bukkit world to WorldEdit world
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            // Convert WorldGuard region to WorldEdit region
            BlockVector3 min = wgRegion.getMinimumPoint();
            BlockVector3 max = wgRegion.getMaximumPoint();
            CuboidRegion region = new CuboidRegion(weWorld, min, max);

            // Create clipboard
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

            // Copy blocks to clipboard
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                    editSession, region, clipboard, region.getMinimumPoint()
                );

                // Copy everything including entities
                copy.setCopyingEntities(true);
                copy.setCopyingBiomes(false); // Don't copy biomes for performance

                Operations.complete(copy);
            }

            // Store clipboard with world-aware key
            String compositeKey = world.getName() + ":" + regionName;
            clipboardCache.put(compositeKey, clipboard);
            knownSchematics.add(compositeKey);

            // Save to disk with world-aware filename
            saveSchematic(compositeKey, clipboard);

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Captured region state for: " + regionName + " in world " + world.getName());
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to capture region: " + regionName + " in world " + world.getName(), e);
            return false;
        }
    }

    // Backward compatibility - uses first world
    @Deprecated
    public boolean captureRegion(String regionName) {
        World world = Bukkit.getWorlds().get(0);
        return captureRegion(regionName, world);
    }

    /**
     * Restores a region to its captured state
     * @param regionName The WorldGuard region name
     * @param world The world the region is in
     * @return true if restoration was successful
     */
    public boolean restoreRegion(String regionName, World world) {
        if (!plugin.getConfigManager().isBlockRestoration()) {
            return false; // Block restoration disabled
        }

        String compositeKey = world.getName() + ":" + regionName;

        // Lazy load: try cache first, then load from disk
        Clipboard clipboard = getOrLoadClipboard(compositeKey);
        if (clipboard == null) {
            plugin.getLogger().warning("No saved state found for region: " + regionName + " in world " + world.getName());
            return false;
        }

        try {
            // Convert Bukkit world to WorldEdit world
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            // Paste the clipboard back
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                // Use ClipboardHolder with builder pattern for WorldEdit 7.3.16+
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation operation = holder.createPaste(editSession)
                    .to(clipboard.getOrigin())
                    .ignoreAirBlocks(false)
                    .build();
                Operations.complete(operation);
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Restored region state for: " + regionName + " in world " + world.getName());
            }

            // Remove from memory and disk if auto-delete is enabled
            if (plugin.getConfigManager().isAutoDeleteSchematics()) {
                clipboardCache.remove(compositeKey);
                knownSchematics.remove(compositeKey);
                deleteSchematic(compositeKey);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore region: " + regionName + " in world " + world.getName(), e);
            return false;
        }
    }

    // Backward compatibility - uses first world
    @Deprecated
    public boolean restoreRegion(String regionName) {
        World world = Bukkit.getWorlds().get(0);
        return restoreRegion(regionName, world);
    }

    /**
     * Checks if a region state has been captured (checks index, doesn't load from disk)
     */
    public boolean hasCapture(String regionName) {
        return knownSchematics.contains(regionName);
    }

    /**
     * Gets the captured clipboard for a region (lazy loads from disk if needed)
     * @param regionName The WorldGuard region name (or composite key world:region)
     * @return The clipboard, or null if not found
     */
    public Clipboard getClipboard(String regionName) {
        return getOrLoadClipboard(regionName);
    }

    /**
     * Deletes a captured region state from cache, index, and disk
     */
    public void deleteCapture(String regionName) {
        clipboardCache.remove(regionName);
        knownSchematics.remove(regionName);
        deleteSchematic(regionName);
    }

    /**
     * Gets the current cache size (for monitoring/debugging)
     */
    public int getCacheSize() {
        return clipboardCache.size();
    }

    /**
     * Gets the total number of known schematics on disk
     */
    public int getTotalSchematicCount() {
        return knownSchematics.size();
    }

    /**
     * Clears the memory cache (schematics remain on disk)
     * Useful for freeing memory if needed
     */
    public void clearCache() {
        clipboardCache.clear();
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Schematic cache cleared");
        }
    }

    /**
     * Lazy loads a clipboard from cache or disk
     * @param compositeKey The composite key (world:region)
     * @return The clipboard, or null if not found
     */
    private Clipboard getOrLoadClipboard(String compositeKey) {
        // Check cache first
        Clipboard cached = clipboardCache.get(compositeKey);
        if (cached != null) {
            return cached;
        }

        // Not in cache - check if it exists on disk
        if (!knownSchematics.contains(compositeKey)) {
            return null;
        }

        // Load from disk
        Clipboard loaded = loadSchematic(compositeKey);
        if (loaded != null) {
            clipboardCache.put(compositeKey, loaded);
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Lazy loaded schematic from disk: " + compositeKey);
            }
        }
        return loaded;
    }

    /**
     * Gets the WorldGuard region
     */
    private ProtectedRegion getWorldGuardRegion(String regionName, World world) {
        RegionManager regionManager = WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            return null;
        }

        return regionManager.getRegion(regionName);
    }

    /**
     * Saves a clipboard to disk using Sponge Schematic format
     * This uses WorldEdit's native format for reliable persistence
     *
     * @param regionName The composite key (world:region)
     * @param clipboard The clipboard to save
     */
    private void saveSchematic(String regionName, Clipboard clipboard) {
        File file = new File(schematicsFolder, regionName + SCHEMATIC_EXTENSION);

        try (FileOutputStream fos = new FileOutputStream(file);
             ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(fos)) {
            writer.write(clipboard);
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Saved schematic: " + regionName + SCHEMATIC_EXTENSION);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save schematic for: " + regionName, e);
        }
    }

    /**
     * Indexes existing schematics without loading them into memory.
     * This allows hasCapture() to work without memory overhead.
     * Also handles migration from legacy .dat files.
     */
    private void indexSchematics() {
        if (!schematicsFolder.exists()) {
            return;
        }

        // Index new .schem files
        File[] schemFiles = schematicsFolder.listFiles((dir, name) -> name.endsWith(SCHEMATIC_EXTENSION));
        if (schemFiles != null) {
            for (File file : schemFiles) {
                String regionName = file.getName().replace(SCHEMATIC_EXTENSION, "");
                knownSchematics.add(regionName);
            }
        }

        // Migration: Handle legacy .dat files
        File[] datFiles = schematicsFolder.listFiles((dir, name) -> name.endsWith(OLD_EXTENSION));
        if (datFiles != null && datFiles.length > 0) {
            plugin.getLogger().warning("Found " + datFiles.length + " legacy .dat file(s) - these are EMPTY due to serialization bug in v2.5.1");
            if (plugin.getConfigManager().isAutoDeleteSchematics()) {
                int deleted = 0;
                for (File datFile : datFiles) {
                    if (datFile.delete()) {
                        deleted++;
                    }
                }
                plugin.getLogger().info("Deleted " + deleted + " empty legacy .dat file(s)");
                if (deleted < datFiles.length) {
                    plugin.getLogger().warning("Could not delete " + (datFiles.length - deleted) + " legacy file(s) - check file permissions");
                }
            } else {
                plugin.getLogger().info("Set 'restoration.auto-delete-schematics: true' to auto-clean legacy files");
            }
        }

        if (!knownSchematics.isEmpty()) {
            plugin.getLogger().info("Indexed " + knownSchematics.size() + " region schematic(s) (lazy loading enabled)");
        }
    }

    /**
     * Loads a schematic from disk using auto-detected format
     * Supports both new .schem format and legacy .dat migration
     *
     * @param regionName The composite key (world:region)
     * @return The loaded clipboard, or null if failed
     */
    private Clipboard loadSchematic(String regionName) {
        // Try new .schem format first
        File schemFile = new File(schematicsFolder, regionName + SCHEMATIC_EXTENSION);

        if (schemFile.exists()) {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
                if (format == null) {
                    plugin.getLogger().warning("Unknown schematic format: " + schemFile.getName());
                    return null;
                }

                try (FileInputStream fis = new FileInputStream(schemFile);
                     ClipboardReader reader = format.getReader(fis)) {
                    Clipboard clipboard = reader.read();
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().info("Loaded schematic: " + regionName + SCHEMATIC_EXTENSION);
                    }
                    return clipboard;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load schematic: " + schemFile.getName(), e);
                return null;
            }
        }

        // Migration: Warn about legacy .dat files (they're empty due to bug)
        File oldFile = new File(schematicsFolder, regionName + OLD_EXTENSION);
        if (oldFile.exists()) {
            plugin.getLogger().warning("Legacy .dat schematic for '" + regionName + "' is empty (serialization bug). Will be re-captured on next rental.");
            if (plugin.getConfigManager().isAutoDeleteSchematics()) {
                if (!oldFile.delete()) {
                    plugin.getLogger().warning("Could not delete legacy schematic: " + oldFile.getName());
                }
            }
        }

        return null;
    }

    /**
     * Deletes a schematic file from disk
     * Handles both new .schem and legacy .dat formats
     *
     * @param regionName The composite key (world:region)
     */
    private void deleteSchematic(String regionName) {
        // Delete new .schem file
        File schemFile = new File(schematicsFolder, regionName + SCHEMATIC_EXTENSION);
        if (schemFile.exists()) {
            if (!schemFile.delete()) {
                plugin.getLogger().warning("Could not delete schematic: " + schemFile.getName());
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Deleted schematic: " + schemFile.getName());
            }
        }

        // Also delete legacy .dat file if it exists (cleanup)
        File oldFile = new File(schematicsFolder, regionName + OLD_EXTENSION);
        if (oldFile.exists()) {
            if (!oldFile.delete()) {
                plugin.getLogger().warning("Could not delete legacy schematic: " + oldFile.getName());
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Deleted legacy schematic: " + oldFile.getName());
            }
        }
    }
}
