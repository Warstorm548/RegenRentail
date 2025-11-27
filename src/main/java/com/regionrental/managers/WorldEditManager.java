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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages WorldEdit integration for block restoration
 * Captures region state when rental starts and restores it when rental expires
 */
public class WorldEditManager {

    private final RegionRental plugin;
    private final Map<String, Clipboard> savedRegions;
    private final File schematicsFolder;

    public WorldEditManager(RegionRental plugin) {
        this.plugin = plugin;
        this.savedRegions = new HashMap<>();
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");

        // Create schematics folder if it doesn't exist
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        loadAllSchematics();
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
            savedRegions.put(compositeKey, clipboard);

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
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            plugin.getLogger().warning("No worlds loaded, cannot capture region: " + regionName);
            return false;
        }
        World world = worlds.get(0);
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
        Clipboard clipboard = savedRegions.get(compositeKey);
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

            // Remove from memory if auto-delete is enabled
            if (plugin.getConfigManager().isAutoDeleteSchematics()) {
                savedRegions.remove(compositeKey);
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
        java.util.List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            plugin.getLogger().warning("No worlds loaded, cannot restore region: " + regionName);
            return false;
        }
        World world = worlds.get(0);
        return restoreRegion(regionName, world);
    }

    /**
     * Checks if a region state has been captured
     */
    public boolean hasCapture(String regionName) {
        return savedRegions.containsKey(regionName);
    }

    /**
     * Gets the captured clipboard for a region
     * @param regionName The WorldGuard region name
     * @return The clipboard, or null if not found
     */
    public Clipboard getClipboard(String regionName) {
        return savedRegions.get(regionName);
    }

    /**
     * Deletes a captured region state
     */
    public void deleteCapture(String regionName) {
        savedRegions.remove(regionName);
        deleteSchematic(regionName);
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
     * Loads all schematics from disk
     */
    private void loadAllSchematics() {
        if (!schematicsFolder.exists()) {
            return;
        }

        File[] files = schematicsFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }

        int loaded = 0;
        for (File file : files) {
            String regionName = file.getName().replace(".dat", "");

            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                SerializableClipboard wrapper = (SerializableClipboard) ois.readObject();
                savedRegions.put(regionName, wrapper.toClipboard());
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load schematic: " + file.getName(), e);
            }
        }

        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " region schematics");
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
