package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manager for EzChestShop integration
 * Handles automatic shop removal from regions when rentals expire
 */
public class EzChestShopManager {

    private final RegionRental plugin;
    private Plugin ezChestShopPlugin;
    private boolean ezChestShopEnabled;

    public EzChestShopManager(RegionRental plugin) {
        this.plugin = plugin;
        setupEzChestShop();
    }

    /**
     * Initialize EzChestShop integration
     */
    private void setupEzChestShop() {
        Plugin ecsPlugin = plugin.getServer().getPluginManager().getPlugin("EzChestShop");

        if (ecsPlugin == null) {
            plugin.getLogger().info("EzChestShop not found - shop removal disabled");
            this.ezChestShopEnabled = false;
            return;
        }

        this.ezChestShopPlugin = ecsPlugin;
        this.ezChestShopEnabled = plugin.getConfigManager().isEzChestShopEnabled();

        if (this.ezChestShopEnabled) {
            plugin.getLogger().info("EzChestShop integration enabled (version: " + ecsPlugin.getDescription().getVersion() + ")");
        } else {
            plugin.getLogger().info("EzChestShop detected but integration disabled in config");
        }
    }

    /**
     * Check if EzChestShop is available and enabled
     */
    public boolean isEnabled() {
        return ezChestShopEnabled && ezChestShopPlugin != null && ezChestShopPlugin.isEnabled();
    }

    /**
     * Remove all chest shops from a WorldGuard region
     * This is called during rental expiration after items have been stored
     *
     * @param regionName The name of the WorldGuard region
     * @return Number of shops removed, or -1 if operation failed
     */
    public int removeShopsInRegion(String regionName) {
        if (!isEnabled()) {
            plugin.getLogger().fine("EzChestShop integration not enabled, skipping shop removal");
            return 0;
        }

        // Get the WorldGuard region
        ProtectedRegion region = plugin.getWorldGuardManager().getRegion(regionName);
        if (region == null) {
            plugin.getLogger().warning("Cannot remove shops from region '" + regionName + "' - region not found");
            return -1;
        }

        // Find the world containing this region
        World world = findWorldForRegion(regionName);
        if (world == null) {
            plugin.getLogger().warning("Cannot remove shops from region '" + regionName + "' - world not found");
            return -1;
        }

        // Get all chest locations in the region
        List<Location> chestLocations = getChestLocationsInRegion(region, world);

        if (chestLocations.isEmpty()) {
            plugin.getLogger().fine("No chests found in region '" + regionName + "'");
            return 0;
        }

        // Remove shops from each chest location
        int shopsRemoved = 0;
        for (Location location : chestLocations) {
            if (removeShopAtLocation(location)) {
                shopsRemoved++;
            }
        }

        if (shopsRemoved > 0) {
            plugin.getLogger().info("Removed " + shopsRemoved + " chest shop(s) from region '" + regionName + "'");
        }

        return shopsRemoved;
    }

    /**
     * Find the world that contains the specified region
     */
    private World findWorldForRegion(String regionName) {
        for (World world : Bukkit.getWorlds()) {
            if (plugin.getWorldGuardManager().regionExists(regionName, world)) {
                return world;
            }
        }
        return null;
    }

    /**
     * Get all chest block locations within a WorldGuard region
     */
    private List<Location> getChestLocationsInRegion(ProtectedRegion region, World world) {
        List<Location> chestLocations = new ArrayList<>();

        // Get region bounds
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Scan all blocks in the region
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockVector3 blockVector = BlockVector3.at(x, y, z);

                    // Check if the block is actually in the region (handles irregular shapes)
                    if (!region.contains(blockVector)) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);

                    // Check if block is a chest-type container that could be a shop
                    if (isChestType(block.getType())) {
                        chestLocations.add(block.getLocation());
                    }
                }
            }
        }

        return chestLocations;
    }

    /**
     * Check if a material is a chest-type block that can be used for shops
     */
    private boolean isChestType(Material material) {
        return material == Material.CHEST ||
               material == Material.TRAPPED_CHEST ||
               material == Material.BARREL;
    }

    /**
     * Remove a shop at a specific location using the /ecsadmin remove command
     *
     * @param location The location of the chest
     * @return true if command was executed successfully
     */
    private boolean removeShopAtLocation(Location location) {
        try {
            // Build the command: /ecsadmin remove <x> <y> <z> <world>
            String command = String.format("ecsadmin remove %d %d %d %s",
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    location.getWorld().getName());

            // Execute command as console (has all permissions)
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (success) {
                plugin.getLogger().fine("Executed shop removal command: /" + command);
            }

            return success;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to remove shop at " + formatLocation(location), e);
            return false;
        }
    }

    /**
     * Format a location for logging
     */
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d) in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld().getName());
    }

    /**
     * Reload the EzChestShop integration (called when config is reloaded)
     */
    public void reload() {
        setupEzChestShop();
    }
}
