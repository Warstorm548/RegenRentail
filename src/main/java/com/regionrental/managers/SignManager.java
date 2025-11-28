package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.regionrental.util.WorldRegionParser;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SignManager {

    private final RegionRental plugin;
    private final Set<String> dirtySigns;  // Track signs that need updates

    public SignManager(RegionRental plugin) {
        this.plugin = plugin;
        this.dirtySigns = new HashSet<>();
    }
    
    public void loadAllSigns() {
        // Migrate existing signs to include support block data
        migrateSupportBlocks();

        // Mark all signs as dirty to update them on startup
        markAllSignsDirty();

        // Update all dirty signs
        updateAllSigns();
    }

    /**
     * Marks all signs as dirty for initial update or full refresh.
     */
    private void markAllSignsDirty() {
        Map<String, Location> signs = plugin.getSignsConfig().getAllSigns();
        dirtySigns.addAll(signs.keySet());
    }

    /**
     * Migrates existing signs to include support block data
     * This is for backward compatibility with signs created before this feature
     */
    private void migrateSupportBlocks() {
        Map<String, Location> signs = plugin.getSignsConfig().getAllSigns();
        int migratedCount = 0;

        for (String compositeKey : signs.keySet()) {
            // Parse composite key "world:region"
            String worldName = WorldRegionParser.extractWorldName(compositeKey);
            String regionName = WorldRegionParser.extractRegionName(compositeKey);

            if (worldName == null) {
                plugin.getLogger().warning("Invalid composite key format: " + compositeKey);
                continue;
            }

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found for sign: " + worldName);
                continue;
            }

            // Skip if support block already exists
            if (plugin.getSignsConfig().hasSupportBlock(regionName, world)) {
                continue;
            }

            Location signLoc = signs.get(compositeKey);
            if (signLoc == null) {
                continue;
            }

            Block signBlock = signLoc.getBlock();
            if (!(signBlock.getState() instanceof Sign)) {
                plugin.getLogger().warning("Sign for region " + compositeKey + " no longer exists at location");
                continue;
            }

            // Detect support block
            Block supportBlock = getSupportBlock(signBlock);
            if (supportBlock != null) {
                // Store the support block information
                Location supportLoc = supportBlock.getLocation();
                String blockType = supportBlock.getType().name();
                String blockData = supportBlock.getBlockData().getAsString();

                plugin.getSignsConfig().addSupportBlock(regionName, world, supportLoc, blockType, blockData);
                migratedCount++;

                plugin.getLogger().info("Migrated support block for region " + compositeKey +
                    " (Type: " + blockType + ")");
            } else {
                plugin.getLogger().warning("Could not detect support block for existing sign in region " + compositeKey);
            }
        }

        if (migratedCount > 0) {
            plugin.getLogger().info("Migrated " + migratedCount + " sign(s) with support block protection");
        }
    }
    
    public void createSign(String regionName, Location location) {
        // Store sign location (addSign uses location's world automatically)
        plugin.getSignsConfig().addSign(regionName, location);

        // Detect and store support block
        Block signBlock = location.getBlock();
        Block supportBlock = getSupportBlock(signBlock);

        if (supportBlock != null) {
            // Store the support block information
            Location supportLoc = supportBlock.getLocation();
            String blockType = supportBlock.getType().name();
            String blockData = supportBlock.getBlockData().getAsString();

            plugin.getSignsConfig().addSupportBlock(regionName, location.getWorld(), supportLoc, blockType, blockData);
            plugin.getLogger().info("Stored support block for region " + location.getWorld().getName() + ":" + regionName +
                " (Type: " + blockType + " at " + supportLoc.getBlockX() + "," +
                supportLoc.getBlockY() + "," + supportLoc.getBlockZ() + ")");
        } else {
            plugin.getLogger().warning("Could not detect support block for sign at " + location);
        }

        // Update the sign
        updateSign(regionName, location.getWorld());

        plugin.getLogger().info("Created rental sign for region " + location.getWorld().getName() + ":" + regionName);
    }

    /**
     * Gets the support block for a sign (the block it's attached to or placed on)
     * @param signBlock The sign block
     * @return The support block, or null if not found
     */
    private Block getSupportBlock(Block signBlock) {
        BlockData blockData = signBlock.getBlockData();

        // Check if it's a wall sign
        if (blockData instanceof WallSign) {
            WallSign wallSign = (WallSign) blockData;
            BlockFace facing = wallSign.getFacing();
            // Wall signs are attached to the block opposite to their facing direction
            return signBlock.getRelative(facing.getOppositeFace());
        }
        // Otherwise it's a standing sign - support block is below
        else if (signBlock.getType().name().contains("SIGN")) {
            return signBlock.getRelative(BlockFace.DOWN);
        }

        return null;
    }
    
    public void removeSign(String regionName, World world) {
        plugin.getSignsConfig().removeSign(regionName, world);
        plugin.getLogger().info("Removed rental sign for region " + world.getName() + ":" + regionName);
    }

    /**
     * Completely removes RegionRental setup from a region
     * This includes removing the sign from config and restoring the support block
     * @param regionName The region to remove setup from
     * @param world The world the region is in
     * @return true if sign was removed, false if no sign existed
     */
    public boolean removeRegionSetup(String regionName, World world) {
        Location location = plugin.getSignsConfig().getSignLocation(regionName, world);

        if (location == null) {
            return false; // No sign exists for this region
        }

        // Restore the support block if it exists
        if (plugin.getSignsConfig().hasSupportBlock(regionName, world)) {
            Location supportLoc = plugin.getSignsConfig().getSupportBlockLocation(regionName, world);
            Map<String, String> supportData = plugin.getSignsConfig().getSupportBlockData(regionName, world);

            if (supportLoc != null && supportData != null) {
                try {
                    Block supportBlock = supportLoc.getBlock();
                    Material originalType = Material.valueOf(supportData.get("type"));
                    String originalData = supportData.get("data");

                    // Restore the block type
                    supportBlock.setType(originalType);

                    // Restore the block data if it exists
                    if (originalData != null && !originalData.isEmpty()) {
                        try {
                            BlockData blockData = plugin.getServer().createBlockData(originalData);
                            supportBlock.setBlockData(blockData);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Could not restore block data for support block: " + e.getMessage());
                        }
                    }

                    plugin.getLogger().info("Restored support block for region " + world.getName() + ":" + regionName +
                        " to " + originalType.name());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Could not restore support block for region " + world.getName() + ":" + regionName +
                        ": Invalid material type - " + e.getMessage());
                }
            }
        } else {
            // No support block data - manually break the sign
            Block block = location.getBlock();
            if (block.getState() instanceof Sign) {
                block.setType(Material.AIR);
            }
        }

        // Remove from config (this removes both sign and support block data)
        plugin.getSignsConfig().removeSign(regionName, world);
        plugin.getLogger().info("Removed RegionRental setup from region " + world.getName() + ":" + regionName);

        return true;
    }
    
    public void updateSign(String regionName, org.bukkit.World world) {
        Location location = plugin.getSignsConfig().getSignLocation(regionName, world);

        if (location == null) {
            return;
        }

        Block block = location.getBlock();

        // Check if block is a sign
        if (!(block.getState() instanceof Sign)) {
            plugin.getLogger().warning("Sign location for " + world.getName() + ":" + regionName + " is not a sign!");
            return;
        }

        Sign sign = (Sign) block.getState();
        Rental rental = plugin.getRentalManager().getRental(regionName, world);

        if (rental == null) {
            // Region is available
            updateAvailableSign(sign, regionName);
        } else {
            // Region is rented
            updateRentedSign(sign, rental);
        }

        sign.update(true);
    }

    // Backward compatibility - deprecated, use world-aware version
    @Deprecated
    public void updateSign(String regionName) {
        // Note: This is deprecated because it doesn't know which world to check
        // It will attempt to find the sign but may not work correctly with multiple worlds
        plugin.getLogger().warning("Deprecated updateSign(regionName) called for: " + regionName +
            " - Please update to use updateSign(regionName, world)");
    }
    
    private void updateAvailableSign(Sign sign, String regionName) {
        World world = sign.getWorld();
        double price = plugin.getConfigManager().getPriceForRegion(regionName, world);
        int duration = plugin.getConfigManager().getDurationForRegion(regionName, world);
        String formattedPrice = String.format(plugin.getConfigManager().getCurrencyFormat(), price);

        int line = 0;
        for (String format : plugin.getConfigManager().getAvailableSignFormat()) {
            if (line >= 4) break;

            String text = format
                .replace("{region}", regionName)
                .replace("{price}", formattedPrice)
                .replace("{duration}", String.valueOf(duration));

            sign.line(line, net.kyori.adventure.text.Component.text(color(text)));
            line++;
        }
    }
    
    private void updateRentedSign(Sign sign, Rental rental) {
        int line = 0;
        for (String format : plugin.getConfigManager().getRentedSignFormat()) {
            if (line >= 4) break;
            
            String text = format
                .replace("{region}", rental.getRegionName())
                .replace("{owner}", rental.getPlayerName())
                .replace("{expires}", rental.getFormattedEndDate())
                .replace("{days}", String.valueOf(rental.getDaysRemaining()))
                .replace("{hours}", String.valueOf(rental.getHoursRemaining()));
            
            sign.line(line, net.kyori.adventure.text.Component.text(color(text)));
            line++;
        }
    }
    
    /**
     * Marks a sign as dirty (needs update).
     * Uses composite key format "world:region".
     *
     * @param regionName The region name
     * @param world The world
     */
    public void markSignDirty(String regionName, World world) {
        String compositeKey = world.getName() + ":" + regionName;
        dirtySigns.add(compositeKey);
    }

    /**
     * Updates all dirty signs and clears the dirty set.
     * This is much more efficient than updating ALL signs every 30 seconds.
     */
    public void updateAllSigns() {
        if (dirtySigns.isEmpty()) {
            return;  // Nothing to update
        }

        Map<String, Location> allSigns = plugin.getSignsConfig().getAllSigns();

        // Update only dirty signs
        for (String compositeKey : dirtySigns) {
            Location location = allSigns.get(compositeKey);
            if (location == null) {
                continue;  // Sign was removed
            }

            // Parse composite key "world:region"
            String regionName = WorldRegionParser.extractRegionName(compositeKey);
            World world = location.getWorld();

            if (world != null) {
                updateSign(regionName, world);
            }
        }

        // Clear dirty set after updating
        dirtySigns.clear();
    }
    
    public boolean isRentalSign(Location location) {
        return plugin.getSignsConfig().getRegionByLocation(location) != null;
    }
    
    public String getRegionFromSign(Location location) {
        return plugin.getSignsConfig().getRegionByLocation(location);
    }

    /**
     * Gets the region name if the given location is a protected support block
     * @param location The block location to check
     * @return Composite key "world:region" if this is a support block, null otherwise
     */
    public String getSupportBlockRegion(Location location) {
        Map<String, Location> signs = plugin.getSignsConfig().getAllSigns();

        for (String compositeKey : signs.keySet()) {
            // Parse composite key "world:region"
            String worldName = WorldRegionParser.extractWorldName(compositeKey);
            String regionName = WorldRegionParser.extractRegionName(compositeKey);

            if (worldName == null) {
                continue;
            }

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                continue;
            }

            Location supportLoc = plugin.getSignsConfig().getSupportBlockLocation(regionName, world);
            if (supportLoc != null && isSameLocation(location, supportLoc)) {
                return compositeKey; // Return full composite key
            }
        }

        return null;
    }

    /**
     * Checks if two locations represent the same block
     */
    private boolean isSameLocation(Location loc1, Location loc2) {
        return loc1.getWorld().getName().equals(loc2.getWorld().getName()) &&
               loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
