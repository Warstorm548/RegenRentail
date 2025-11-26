package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
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

    // Reflection cache
    private Class<?> shopContainerClass;
    private Method getShopMethod;
    private Method deleteShopMethod;
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

            try {
                deleteShopMethod = shopContainerClass.getMethod("deleteShop", Location.class);
            } catch (NoSuchMethodException e) {
                try {
                    deleteShopMethod = shopContainerClass.getMethod("removeShop", Location.class);
                } catch (NoSuchMethodException e2) {
                    plugin.getLogger().warning("No shop deletion method found");
                }
            }

            if ((getShopMethod != null || isShopMethod != null)) {
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

        boolean shopRemoved = false;

        // Try direct API deletion
        if (deleteShopMethod != null) {
            try {
                deleteShopMethod.invoke(null, location);
                if (!hasShopAt(location)) {
                    if (debug) {
                        plugin.getLogger().info("[Debug] Shop removed via API at " + formatLocation(location));
                    }
                    shopRemoved = true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("API deletion failed: " + e.getMessage());
            }
        }

        // Fallback: block break method
        if (!shopRemoved) {
            shopRemoved = removeShopViaBlockBreak(location);
        }

        // Schedule hologram cleanup after a short delay (3 ticks = 0.15s)
        // Delay ensures EzChestShop has processed the BlockBreakEvent first
        if (shopRemoved) {
            Location shopLocation = location.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeHologramEntities(shopLocation);
            }, 3L);
        }

        return shopRemoved;
    }

    private boolean removeShopViaBlockBreak(Location location) {
        Block block = location.getBlock();
        Material originalType = block.getType();

        if (!SHOP_CONTAINER_TYPES.contains(originalType)) {
            return false;
        }

        boolean debug = plugin.getConfigManager().isDebugMode();

        try {
            // Clear inventory to prevent drops
            if (block.getState() instanceof Container container) {
                container.getInventory().clear();
            }

            // Save block data for potential restoration
            org.bukkit.block.data.BlockData blockData = block.getBlockData().clone();

            // Fire BlockBreakEvent so EzChestShop's listener can delete shop data
            Player breaker = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (breaker != null) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(block, breaker);
                breakEvent.setDropItems(false);  // Prevent item drops
                Bukkit.getPluginManager().callEvent(breakEvent);

                if (debug) {
                    plugin.getLogger().info("[Debug] Fired BlockBreakEvent for shop removal at " + formatLocation(location));
                }
            }

            // Now break the block (EzChestShop has already processed the event above)
            block.setType(Material.AIR);

            // Restore block if WorldEdit won't
            if (!plugin.getConfigManager().isBlockRestoration()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (block.getType() == Material.AIR) {
                        block.setBlockData(blockData);
                    }
                }, 3L);
            }

            if (debug) {
                plugin.getLogger().info("[Debug] Block-break removal completed at " + formatLocation(location));
            }
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Block-break removal failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove hologram entities (armor stands and floating items) near a shop location.
     * EzChestShop uses invisible armor stands and items for hologram displays.
     *
     * @param location The location where the shop was removed
     */
    private void removeHologramEntities(Location location) {
        boolean debug = plugin.getConfigManager().isDebugMode();
        World world = location.getWorld();
        if (world == null) return;

        // Search center: 0.5 blocks offset (center of block) + 1.5 blocks up (where holograms typically appear)
        Location searchCenter = location.clone().add(0.5, 1.5, 0.5);

        // Search radius: 1.5 horizontal, 2.5 vertical to catch all hologram entities
        world.getNearbyEntities(searchCenter, 1.5, 2.5, 1.5).forEach(entity -> {
            // Remove invisible/marker armor stands (hologram text lines)
            if (entity instanceof ArmorStand armorStand) {
                if (!armorStand.isVisible() || armorStand.isMarker() || armorStand.isSmall() || !armorStand.hasGravity()) {
                    if (debug) {
                        plugin.getLogger().info("[Debug] Removing hologram armor stand at " + formatLocation(entity.getLocation()));
                    }
                    armorStand.remove();
                }
            }
            // Remove floating items (hologram item displays)
            if (entity instanceof Item item) {
                if (item.getPickupDelay() >= 32767 || item.isInvulnerable() || !item.hasGravity()) {
                    if (debug) {
                        plugin.getLogger().info("[Debug] Removing floating hologram item at " + formatLocation(entity.getLocation()));
                    }
                    item.remove();
                }
            }
        });
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
        this.deleteShopMethod = null;
        this.isShopMethod = null;
        initializeReflection();
    }
}
