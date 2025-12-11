package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;

/**
 * Manages WorldEdit integration for block restoration
 * Captures region state when rental starts and restores it when rental expires
 */
public class WorldEditManager {

    private final RegionRental plugin;
    private final File schematicsFolder;

    // LRU cache for clipboards - limits memory usage
    private static final int DEFAULT_CACHE_SIZE = 20;
    private final int maxCacheSize;
    private final Map<String, Clipboard> clipboardCache;

    // Track which schematics exist on disk (for hasCapture checks without loading)
    private final Set<String> knownSchematics = new HashSet<>();

    public WorldEditManager(RegionRental plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        this.maxCacheSize = plugin.getConfigManager().getSchematicCacheSize(DEFAULT_CACHE_SIZE);

        // Create LRU cache with access-order ordering
        this.clipboardCache = new LinkedHashMap<String, Clipboard>(maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Clipboard> eldest) {
                boolean shouldRemove = size() > maxCacheSize;
                if (shouldRemove && plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("Evicting schematic from cache: " + eldest.getKey());
                }
                return shouldRemove;
            }
        };

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
     * Saves a clipboard to disk as a serialized object
     * Note: This uses Java serialization for simplicity
     * Could be enhanced to use Sponge Schematic format in the future
     */
    private void saveSchematic(String regionName, Clipboard clipboard) {
        File file = new File(schematicsFolder, regionName + ".dat");

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            // Create a serializable wrapper
            SerializableClipboard wrapper = new SerializableClipboard(clipboard);
            oos.writeObject(wrapper);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save schematic for: " + regionName, e);
        }
    }

    /**
     * Indexes existing schematics without loading them into memory.
     * This allows hasCapture() to work without memory overhead.
     */
    private void indexSchematics() {
        if (!schematicsFolder.exists()) {
            return;
        }

        File[] files = schematicsFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String regionName = file.getName().replace(".dat", "");
            knownSchematics.add(regionName);
        }

        if (!knownSchematics.isEmpty()) {
            plugin.getLogger().info("Indexed " + knownSchematics.size() + " region schematics (lazy loading enabled)");
        }
    }

    /**
     * Loads a single schematic from disk
     * @param regionName The region name (composite key)
     * @return The loaded clipboard, or null if failed
     */
    private Clipboard loadSchematic(String regionName) {
        File file = new File(schematicsFolder, regionName + ".dat");
        if (!file.exists()) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            SerializableClipboard wrapper = (SerializableClipboard) ois.readObject();
            return wrapper.toClipboard();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load schematic: " + file.getName(), e);
            return null;
        }
    }

    /**
     * Deletes a schematic file from disk
     */
    private void deleteSchematic(String regionName) {
        File file = new File(schematicsFolder, regionName + ".dat");
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Wrapper class for serializing WorldEdit clipboards
     * This is a simplified implementation - in production, consider using
     * WorldEdit's native Sponge Schematic format
     */
    private static class SerializableClipboard implements Serializable {
        private static final long serialVersionUID = 1L;

        private final transient Clipboard clipboard;

        public SerializableClipboard(Clipboard clipboard) {
            this.clipboard = clipboard;
        }

        public Clipboard toClipboard() {
            return clipboard;
        }
    }
}
