package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import com.regionrental.util.WorldRegionParser;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to teleport players to their rented regions
 * Supports owners and members
 */
public class TpCommand implements CommandExecutor, TabCompleter {

    private final RegionRental plugin;

    public TpCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Player-only command
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Permission check
        if (!player.hasPermission("regionrental.tp")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Check if teleport is enabled
        if (!plugin.getConfigManager().isTeleportEnabled()) {
            player.sendMessage(ChatColor.RED + "Teleportation is currently disabled!");
            return true;
        }

        // Validate arguments
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <region>");
            player.sendMessage(ChatColor.YELLOW + "Example: /" + label + " shop1");
            player.sendMessage(ChatColor.YELLOW + "Example: /" + label + " world:shop1");
            return true;
        }

        // Parse region with world awareness
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(args[0], player);
        if (parsed == null) {
            player.sendMessage(ChatColor.RED + "Invalid region format!");
            player.sendMessage(ChatColor.YELLOW + "Use: <region> or world:<region>");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        // Check if region exists in WorldGuard
        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            player.sendMessage(plugin.getConfigManager().getMessage("region-not-found",
                    "{region}", parsed.getCompositeKey()));
            return true;
        }

        // Get rental
        Rental rental = plugin.getRentalManager().getRental(regionName, world);
        if (rental == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("tp-not-rented",
                    "{region}", parsed.getCompositeKey()));
            return true;
        }

        // Check if player is owner or member
        boolean isOwner = rental.getPlayerUUID().equals(player.getUniqueId());
        boolean isMember = rental.hasMember(player.getUniqueId());

        if (!isOwner && !isMember) {
            player.sendMessage(plugin.getConfigManager().getMessage("tp-no-permission",
                    "{region}", parsed.getCompositeKey()));
            return true;
        }

        // Check cooldown (bypass for admins)
        if (!player.hasPermission("regionrental.admin.bypass")) {
            if (plugin.getTeleportCooldownManager().isOnCooldown(player)) {
                int remaining = plugin.getTeleportCooldownManager().getRemainingCooldown(player);
                player.sendMessage(plugin.getConfigManager().getMessage("tp-cooldown",
                        "{time}", String.valueOf(remaining)));
                return true;
            }
        }

        // Get sign location
        Location signLocation = plugin.getSignsConfig().getSignLocation(regionName, world);
        if (signLocation == null) {
            player.sendMessage(ChatColor.RED + "No rental sign found for region " + parsed.getCompositeKey());
            player.sendMessage(ChatColor.YELLOW + "Please contact an administrator.");
            return true;
        }

        // Find safe location
        Block signBlock = signLocation.getBlock();
        int maxSearchDistance = plugin.getConfigManager().getTeleportMaxSearchDistance();
        Location safeLocation = findSafeLocation(signLocation, signBlock, player, maxSearchDistance);

        if (safeLocation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("tp-no-safe-location",
                    "{region}", parsed.getCompositeKey()));
            return true;
        }

        // Show cross-world warning if applicable
        if (plugin.getConfigManager().isTeleportCrossWorldWarning()) {
            if (!player.getWorld().equals(world)) {
                player.sendMessage(plugin.getConfigManager().getMessage("tp-cross-world-warning",
                        "{region}", parsed.getCompositeKey(),
                        "{world}", world.getName()));
            }
        }

        // Perform teleport
        boolean success = player.teleport(safeLocation);

        if (success) {
            // Play sound effect
            if (plugin.getConfigManager().isTeleportSoundEnabled()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }

            // Spawn particles
            if (plugin.getConfigManager().isTeleportParticleEnabled()) {
                player.getWorld().spawnParticle(Particle.PORTAL, safeLocation, 50, 0.5, 1.0, 0.5, 0.1);
            }

            // Success message
            player.sendMessage(plugin.getConfigManager().getMessage("tp-success",
                    "{region}", parsed.getCompositeKey()));

            // Start cooldown (bypass for admins)
            if (!player.hasPermission("regionrental.admin.bypass")) {
                plugin.getTeleportCooldownManager().startCooldown(player);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Teleport failed! Please try again.");
        }

        return true;
    }

    /**
     * Finds a safe teleport location starting from the sign location
     * Searches upward only (no downward search)
     *
     * @param signLocation The location of the rental sign
     * @param signBlock The sign block
     * @param player The player teleporting
     * @param maxSearchDistance Maximum blocks to search upward
     * @return Safe location or null if none found
     */
    private Location findSafeLocation(Location signLocation, Block signBlock, Player player, int maxSearchDistance) {
        // Get starting location (1 block in front of sign)
        Location startLocation = getLocationInFrontOfSign(signLocation, signBlock, player);

        // Search upward for safe location
        for (int y = 0; y <= maxSearchDistance; y++) {
            Location testLocation = startLocation.clone().add(0, y, 0);

            if (isSafeLocation(testLocation)) {
                // Center the location and preserve yaw
                Location safeLocation = testLocation.clone().add(0.5, 0, 0.5);
                safeLocation.setYaw(startLocation.getYaw());
                safeLocation.setPitch(0);
                return safeLocation;
            }
        }

        return null; // No safe location found
    }

    /**
     * Get the location 1 block in front of the sign
     * For wall signs: uses sign's facing direction
     * For standing signs: uses player's current facing direction
     *
     * @param signLocation The sign's location
     * @param signBlock The sign block
     * @param player The player
     * @return Location in front of sign
     */
    private Location getLocationInFrontOfSign(Location signLocation, Block signBlock, Player player) {
        BlockData blockData = signBlock.getBlockData();
        Location frontLocation = signLocation.clone();

        if (blockData instanceof WallSign) {
            // Wall sign - use sign's facing direction
            WallSign wallSign = (WallSign) blockData;
            BlockFace facing = wallSign.getFacing();

            // Move 1 block in the direction the sign is facing
            frontLocation = signLocation.clone().add(facing.getModX(), 0, facing.getModZ());

            // Set yaw to match sign's facing direction
            frontLocation.setYaw(getYawFromBlockFace(facing));
            frontLocation.setPitch(0);
        } else {
            // Standing sign - use player's current facing
            frontLocation = signLocation.clone();
            frontLocation.setYaw(player.getLocation().getYaw());
            frontLocation.setPitch(0);
        }

        return frontLocation;
    }

    /**
     * Convert BlockFace to player yaw angle
     *
     * @param face The block face
     * @return Yaw angle in degrees
     */
    private float getYawFromBlockFace(BlockFace face) {
        switch (face) {
            case SOUTH:
                return 0.0f;
            case WEST:
                return 90.0f;
            case NORTH:
                return 180.0f;
            case EAST:
                return 270.0f;
            default:
                return 0.0f;
        }
    }

    /**
     * Check if a location is safe for teleportation
     * Requirements:
     * - Solid floor block (not dangerous)
     * - Passable feet and head blocks
     * - No dangerous blocks at feet/head level
     *
     * @param location The location to check
     * @return true if safe, false otherwise
     */
    private boolean isSafeLocation(Location location) {
        Block feetBlock = location.getBlock();
        Block headBlock = location.clone().add(0, 1, 0).getBlock();
        Block floorBlock = location.clone().subtract(0, 1, 0).getBlock();

        // Floor must be solid and not dangerous
        if (!floorBlock.getType().isSolid() || isDangerousBlock(floorBlock)) {
            return false;
        }

        // Feet and head must be passable (air, water, etc.)
        if (!isPassableBlock(feetBlock) || !isPassableBlock(headBlock)) {
            return false;
        }

        // Feet and head must not be dangerous
        if (isDangerousBlock(feetBlock) || isDangerousBlock(headBlock)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a block is passable (player can stand in it)
     *
     * @param block The block to check
     * @return true if passable
     */
    private boolean isPassableBlock(Block block) {
        Material type = block.getType();

        // Air is always passable
        if (type.isAir()) {
            return true;
        }

        // Other passable blocks
        return type == Material.WATER ||
                type == Material.LAVA || // Lava is passable but dangerous (checked separately)
                type.name().contains("SIGN") ||
                type.name().contains("TORCH") ||
                type.name().contains("FLOWER") ||
                type.name().contains("GRASS") ||
                type.name().contains("TALL_GRASS") ||
                type.name().contains("SEAGRASS") ||
                type.name().contains("KELP") ||
                type == Material.SNOW ||
                type.name().contains("RAIL") ||
                type.name().contains("CARPET") ||
                type.name().contains("PRESSURE_PLATE") ||
                type.name().contains("BUTTON") ||
                type.name().contains("TRIPWIRE");
    }

    /**
     * Check if a block is dangerous to stand in/on
     *
     * @param block The block to check
     * @return true if dangerous
     */
    private boolean isDangerousBlock(Block block) {
        Material type = block.getType();

        return type == Material.LAVA ||
                type == Material.FIRE ||
                type == Material.SOUL_FIRE ||
                type == Material.CACTUS ||
                type == Material.MAGMA_BLOCK ||
                type == Material.WITHER_ROSE ||
                type.name().contains("CAMPFIRE") ||
                type == Material.SWEET_BERRY_BUSH ||
                type == Material.POWDER_SNOW;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;

            // Add rentals owned by player
            List<Rental> playerRentals = plugin.getRentalManager().getPlayerRentals(player.getUniqueId());
            for (Rental rental : playerRentals) {
                completions.add(rental.getRegionName());
                completions.add(rental.getCompositeKey());
            }

            // Add rentals where player is a member
            List<Rental> memberRentals = plugin.getRentalManager().getRentalsWhereMember(player.getUniqueId());
            for (Rental rental : memberRentals) {
                completions.add(rental.getRegionName());
                completions.add(rental.getCompositeKey());
            }

            // Filter and return
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
