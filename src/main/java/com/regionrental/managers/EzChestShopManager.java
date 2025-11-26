package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manager for EzChestShop integration.
 * Uses reflection to access EzChestShop API and removes shops before region regeneration.
 */
public class EzChestShopManager {

    private final RegionRental plugin;
    private boolean ezChestShopEnabled;

    // Reflection cache for shop detection
    private Class<?> shopContainerClass;
    private Method getShopMethod;
    private Method isShopMethod;

    // Container types that can be EzChestShop shops
    private static final Set<Material> SHOP_CONTAINER_TYPES = Set.of(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL
    );

    public EzChestShopManager(RegionRental plugin) {
        this.plugin = plugin;
        this.ezChestShopEnabled = false;
        initializeReflection();
    }

    private void initializeReflection() {
        // Try multiple possible plugin names (including Reborn variant)
        String[] possibleNames = {"EzChestShop", "EzChestShopReborn", "ecs", "ChestShop", "ezchestshop"};
        Plugin ezChestShop = null;

        for (String name : possibleNames) {
            ezChestShop = Bukkit.getPluginManager().getPlugin(name);
            if (ezChestShop != null) {
                plugin.getLogger().info("Found EzChestShop plugin as: " + name);
                break;
            }
        }

        if (ezChestShop == null) {
            plugin.getLogger().info("EzChestShop not detected - shop removal integration disabled");
            plugin.getLogger().info("Tried names: " + String.join(", ", possibleNames));
            return;
        }

        // Check if plugin is enabled - if not, schedule delayed initialization
        if (!ezChestShop.isEnabled()) {
            plugin.getLogger().info("EzChestShop found but not yet enabled - will retry in 1 second...");
            final Plugin finalEzChestShop = ezChestShop;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finalEzChestShop.isEnabled()) {
                    plugin.getLogger().info("EzChestShop now enabled - initializing integration...");
                    continueInitialization(finalEzChestShop);
                } else {
                    plugin.getLogger().warning("EzChestShop still not enabled after delay - integration disabled");
                }
            }, 20L); // 1 second delay
            return;
        }

        // Plugin is already enabled - continue immediately
        continueInitialization(ezChestShop);
    }

    private void continueInitialization(Plugin ezChestShop) {
        plugin.getLogger().info("EzChestShop detected (v" + ezChestShop.getDescription().getVersion() + ") - initializing integration...");

        try {
            shopContainerClass = Class.forName("me.deadlight.ezchestshop.data.ShopContainer");

            // Try different method names for compatibility
            try {
                getShopMethod = shopContainerClass.getMethod("getShop", Location.class);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().fine("getShop(Location) not found");
            }

            try {
                isShopMethod = shopContainerClass.getMethod("isShop", Location.class);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().fine("isShop(Location) not found");
            }

            if (getShopMethod != null || isShopMethod != null) {
                ezChestShopEnabled = true;
                plugin.getLogger().info("EzChestShop integration enabled successfully");
            } else {
                plugin.getLogger().warning("EzChestShop integration failed - API methods not found");
            }

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("EzChestShop ShopContainer class not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize EzChestShop integration", e);
        }
    }

    public boolean isEnabled() {
        return ezChestShopEnabled;
    }

    public boolean hasShopAt(Location location) {
        if (!ezChestShopEnabled) return false;

        try {
            if (isShopMethod != null) {
                Object result = isShopMethod.invoke(null, location);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
            if (getShopMethod != null) {
                Object shop = getShopMethod.invoke(null, location);
                return shop != null;
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().fine("Error checking shop at " + formatLocation(location) + ": " + e.getMessage());
            }
        }
        return false;
    }

    public boolean removeShopAt(Location location) {
        if (!ezChestShopEnabled) return false;

        boolean debug = plugin.getConfigManager().isDebugMode();

        if (!hasShopAt(location)) {
            if (debug) {
                plugin.getLogger().info("[Debug] No shop found at " + formatLocation(location));
            }
            return false;
        }

        if (debug) {
            plugin.getLogger().info("[Debug] Removing shop at " + formatLocation(location));
        }

        // Execute /ecs remove command
        // This lets EzChestShop handle all cleanup including holograms, data, etc.
        String command = String.format("ecs remove %d %d %d %s",
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            location.getWorld().getName());

        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (debug) {
                plugin.getLogger().info("[Debug] Executed command: /" + command);
            }

            // Verify shop was removed
            boolean removed = !hasShopAt(location);

            if (removed && debug) {
                plugin.getLogger().info("[Debug] Shop successfully removed at " + formatLocation(location));
            } else if (!removed) {
                plugin.getLogger().warning("Shop removal command failed at " + formatLocation(location));
            }

            return removed;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute shop removal command: " + e.getMessage());
            return false;
        }
    }


    /**
     * Remove all chest shops from a WorldGuard region.
     * This method maintains the original signature for backward compatibility.
     *
     * @param regionName The name of the WorldGuard region
     * @return Number of shops removed
     */
    public int removeShopsInRegion(String regionName) {
        if (!ezChestShopEnabled) return 0;

        if (!plugin.getConfigManager().isEzChestShopRemovalEnabled()) {
            return 0;
        }

        // Get the WorldGuard region
        ProtectedRegion region = plugin.getWorldGuardManager().getRegion(regionName);
        if (region == null) {
            plugin.getLogger().warning("Cannot remove shops from region '" + regionName + "' - region not found");
            return 0;
        }

        // Find the world containing this region
        World world = findWorldForRegion(regionName);
        if (world == null) {
            plugin.getLogger().warning("Cannot remove shops from region '" + regionName + "' - world not found");
            return 0;
        }

        return removeShopsInRegion(region, world, regionName);
    }

    /**
     * Internal method to remove shops from a region.
     *
     * @param region The WorldGuard region
     * @param world The world containing the region
     * @param regionId The region ID for logging
     * @return Number of shops removed
     */
    private int removeShopsInRegion(ProtectedRegion region, World world, String regionId) {
        boolean debug = plugin.getConfigManager().isDebugMode();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        if (debug) {
            plugin.getLogger().info("[Debug] Scanning region '" + regionId + "' for shops");
        }

        // Find all shops
        List<Location> shopLocations = new ArrayList<>();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = loc.getBlock();
                    if (SHOP_CONTAINER_TYPES.contains(block.getType()) && hasShopAt(loc)) {
                        shopLocations.add(loc);
                    }
                }
            }
        }

        if (shopLocations.isEmpty()) {
            if (debug) {
                plugin.getLogger().info("[Debug] No shops found in region '" + regionId + "'");
            }
            return 0;
        }

        plugin.getLogger().info("Found " + shopLocations.size() + " EzChestShop(s) in region '" + regionId + "'");

        // Remove all shops
        int removedCount = 0;
        for (Location shopLoc : shopLocations) {
            if (removeShopAt(shopLoc)) {
                removedCount++;
            }
        }

        plugin.getLogger().info("Removed " + removedCount + "/" + shopLocations.size() + " shop(s) from '" + regionId + "'");
        return removedCount;
    }

    /**
     * Find the world that contains the specified region.
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
     * Notify a player that shops were removed from their rental.
     *
     * @param playerUUID The player's UUID
     * @param regionId The region ID
     * @param shopCount Number of shops removed
     */
    public void notifyPlayer(UUID playerUUID, String regionId, int shopCount) {
        if (!plugin.getConfigManager().isEzChestShopNotifyEnabled()) return;

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            String message = plugin.getConfigManager().getEzChestShopRemovalMessage()
                .replace("{region}", regionId)
                .replace("{count}", String.valueOf(shopCount))
                .replace("&", "§");
            player.sendMessage(message);
        }
    }

    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " @ " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void reload() {
        this.ezChestShopEnabled = false;
        this.shopContainerClass = null;
        this.getShopMethod = null;
        this.isShopMethod = null;
        initializeReflection();
    }
}
