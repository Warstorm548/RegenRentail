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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manager for EzChestShop integration
 * Handles automatic shop removal from regions when rentals expire
 * Uses reflection to access EzChestShop's internal API (no compile-time dependency)
 */
public class EzChestShopManager {

    private final RegionRental plugin;
    private Plugin ezChestShopPlugin;
    private boolean ezChestShopEnabled;

    // Cached reflection objects for performance
    private Class<?> shopContainerClass;
    private Object shopContainerInstance;
    private Method isShopMethod;
    private Method deleteShopMethod;
    private Method hideForAllMethod;
    private boolean apiInitialized = false;

    public EzChestShopManager(RegionRental plugin) {
        this.plugin = plugin;
        setupEzChestShop();
    }

    /**
     * Initialize EzChestShop integration and reflection API
     */
    private void setupEzChestShop() {
        Plugin ecsPlugin = plugin.getServer().getPluginManager().getPlugin("EzChestShop");

        if (ecsPlugin == null) {
            plugin.getLogger().info("EzChestShop not found - shop removal disabled");
            this.ezChestShopEnabled = false;
            this.apiInitialized = false;
            return;
        }

        this.ezChestShopPlugin = ecsPlugin;
        this.ezChestShopEnabled = plugin.getConfigManager().isEzChestShopEnabled();

        if (!this.ezChestShopEnabled) {
            plugin.getLogger().info("EzChestShop detected but integration disabled in config");
            this.apiInitialized = false;
            return;
        }

        // Initialize reflection API
        this.apiInitialized = initializeReflectionAPI();

        if (this.apiInitialized) {
            plugin.getLogger().info("EzChestShop integration enabled (version: " + ecsPlugin.getDescription().getVersion() + ")");
        } else {
            plugin.getLogger().warning("EzChestShop found but API initialization failed - shop removal will be disabled");
            this.ezChestShopEnabled = false;
        }
    }

    /**
     * Initialize reflection objects for accessing EzChestShop's internal API
     *
     * @return true if initialization succeeded, false otherwise
     */
    private boolean initializeReflectionAPI() {
        try {
            // Load ShopContainer class
            shopContainerClass = Class.forName("me.deadlight.ezchestshop.data.ShopContainer");

            // Get ShopContainer instance from EzChestShop plugin
            Method getShopContainerMethod = ezChestShopPlugin.getClass().getMethod("getShopContainer");
            shopContainerInstance = getShopContainerMethod.invoke(ezChestShopPlugin);

            if (shopContainerInstance == null) {
                plugin.getLogger().warning("Failed to get ShopContainer instance from EzChestShop");
                return false;
            }

            // Cache the methods we'll need
            isShopMethod = shopContainerClass.getMethod("isShop", Location.class);
            deleteShopMethod = shopContainerClass.getMethod("deleteShop", Location.class);

            // Load ShopHologram class for hologram cleanup
            Class<?> shopHologramClass = Class.forName("me.deadlight.ezchestshop.utils.holograms.ShopHologram");
            hideForAllMethod = shopHologramClass.getMethod("hideForAll", Location.class);

            plugin.getLogger().fine("EzChestShop API reflection initialized successfully");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("EzChestShop ShopContainer class not found - incompatible version?");
            return false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("EzChestShop API methods not found - incompatible version: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize EzChestShop API reflection", e);
            return false;
        }
    }

    /**
     * Check if EzChestShop is available, enabled, and API is initialized
     */
    public boolean isEnabled() {
        return ezChestShopEnabled && ezChestShopPlugin != null && ezChestShopPlugin.isEnabled() && apiInitialized;
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
     * Remove a shop at a specific location using EzChestShop's internal API via reflection
     *
     * @param location The location of the chest
     * @return true if a shop was found and removed, false otherwise
     */
    private boolean removeShopAtLocation(Location location) {
        if (!apiInitialized) {
            return false;
        }

        try {
            // Check if this location has a shop
            Boolean isShop = (Boolean) isShopMethod.invoke(shopContainerInstance, location);

            if (isShop == null || !isShop) {
                // No shop at this location
                return false;
            }

            // STEP 1: Hide holograms for all players BEFORE deletion
            hideForAllMethod.invoke(null, location); // static method, pass null for instance

            // STEP 2: Delete the shop from database and memory
            deleteShopMethod.invoke(shopContainerInstance, location);

            // STEP 3: Hide holograms again as safety measure (handles async packet timing)
            hideForAllMethod.invoke(null, location);

            plugin.getLogger().fine("Successfully removed EzChestShop and holograms at " + formatLocation(location));
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to remove shop at " + formatLocation(location) + " via reflection", e);
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
     * Reinitializes reflection API in case plugin was updated
     */
    public void reload() {
        // Reset state
        this.apiInitialized = false;
        this.shopContainerClass = null;
        this.shopContainerInstance = null;
        this.isShopMethod = null;
        this.deleteShopMethod = null;
        this.hideForAllMethod = null;

        // Reinitialize
        setupEzChestShop();
    }
}
