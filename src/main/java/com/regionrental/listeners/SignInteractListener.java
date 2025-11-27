package com.regionrental.listeners;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignInteractListener implements Listener {
    
    private final RegionRental plugin;
    
    public SignInteractListener(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if player clicked on a sign
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        if (event.getClickedBlock() == null || 
            !(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }
        
        Location signLoc = event.getClickedBlock().getLocation();
        String regionName = plugin.getSignManager().getRegionFromSign(signLoc);
        
        if (regionName == null) {
            // Not a rental sign
            return;
        }
        
        event.setCancelled(true);
        Player player = event.getPlayer();
        
        // Check if player is shift-clicking (extend rental)
        if (player.isSneaking()) {
            handleExtendRental(player, regionName);
        } else {
            // Regular click - rent or show info
            handleRentOrInfo(player, regionName);
        }
    }
    
    private void handleRentOrInfo(Player player, String regionName) {
        Rental rental = plugin.getRentalManager().getRental(regionName, player.getWorld());

        if (rental == null) {
            // Region is available - attempt to rent
            handleRentRegion(player, regionName);
        } else {
            // Region is rented - show info
            showRentalInfo(player, rental);
        }
    }
    
    private void handleRentRegion(Player player, String regionName) {
        // Check permission
        if (!player.hasPermission("regionrental.rent")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        
        // Check if region exists in WorldGuard (FIXED: use world-aware method)
        if (!plugin.getWorldGuardManager().regionExists(regionName, player.getWorld())) {
            player.sendMessage(plugin.getConfigManager().getMessage("region-not-found",
                "{region}", regionName));
            return;
        }
        
        // Check player rental limit
        int currentRentals = plugin.getRentalManager().getPlayerRentals(player.getUniqueId()).size();
        int maxRentals = plugin.getConfigManager().getMaxRentalsPerPlayer();
        
        if (currentRentals >= maxRentals) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-rentals-reached"));
            return;
        }
        
        // Get price
        double price = plugin.getConfigManager().getPriceForRegion(regionName);
        
        // Check for permission-based pricing
        for (String perm : plugin.getConfigManager().getPermissionPrices().keySet()) {
            if (player.hasPermission(perm)) {
                price = plugin.getConfigManager().getPermissionPrices().get(perm);
                break;
            }
        }
        
        // Check economy
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy system not available!");
            return;
        }
        
        if (!economy.has(player, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-enough-money",
                "{amount}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));
            return;
        }
        
        // Withdraw money
        economy.withdrawPlayer(player, price);
        
        // Create rental
        int days = plugin.getConfigManager().getDurationForRegion(regionName);
        if (plugin.getRentalManager().createRental(regionName, player.getWorld(), player, days, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-success",
                "{region}", regionName,
                "{days}", String.valueOf(days),
                "{price}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));

            // Play sound
            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            // Refund if rental failed
            economy.depositPlayer(player, price);
            player.sendMessage(ChatColor.RED + "Failed to create rental!");
        }
    }
    
    private void handleExtendRental(Player player, String regionName) {
        // Check permission
        if (!player.hasPermission("regionrental.extend")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        Rental rental = plugin.getRentalManager().getRental(regionName, player.getWorld());

        if (rental == null) {
            player.sendMessage(ChatColor.RED + "This region is not rented!");
            return;
        }

        // Check if player owns this rental
        if (!rental.getPlayerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this rental!");
            return;
        }

        // Check extension limit
        if (rental.getExtensionCount() >= plugin.getConfigManager().getMaxExtensions()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-extensions-reached"));
            return;
        }

        // Get extension price (same as rental price by default)
        double price = plugin.getConfigManager().getPriceForRegion(regionName);
        double multiplier = plugin.getConfig().getDouble("extension.price-multiplier", 1.0);
        price = price * multiplier;

        // Check economy
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy system not available!");
            return;
        }

        if (!economy.has(player, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-enough-money",
                "{amount}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));
            return;
        }

        // Withdraw money
        economy.withdrawPlayer(player, price);

        // Extend rental
        int days = plugin.getConfigManager().getExtensionDuration();
        if (plugin.getRentalManager().extendRental(regionName, player.getWorld(), player, days, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-extended",
                "{region}", regionName,
                "{days}", String.valueOf(days),
                "{price}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));

            // Play sound
            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            // Refund if extension failed
            economy.depositPlayer(player, price);
            player.sendMessage(ChatColor.RED + "Failed to extend rental!");
        }
    }
    
    private void showRentalInfo(Player player, Rental rental) {
        player.sendMessage(ChatColor.GOLD + "=== Rental Info ===");
        player.sendMessage(ChatColor.YELLOW + "Region: " + ChatColor.WHITE + rental.getRegionName());
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + rental.getPlayerName());
        player.sendMessage(ChatColor.YELLOW + "Expires: " + ChatColor.WHITE + rental.getFormattedEndDate());
        player.sendMessage(ChatColor.YELLOW + "Time Remaining: " + ChatColor.WHITE + 
            rental.getDaysRemaining() + " days, " + 
            (rental.getHoursRemaining() % 24) + " hours");
        player.sendMessage(ChatColor.YELLOW + "Extensions Used: " + ChatColor.WHITE + 
            rental.getExtensionCount() + "/" + plugin.getConfigManager().getMaxExtensions());
        
        if (rental.getPlayerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "Shift+Right-Click to extend your rental!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        // Protect rental signs if configured
        if (!plugin.getConfigManager().isSignProtection()) {
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        // Check if the block being broken is the sign itself
        if (event.getBlock().getState() instanceof Sign) {
            if (plugin.getSignManager().isRentalSign(blockLoc)) {
                // Check if player has admin permission
                if (!event.getPlayer().hasPermission("regionrental.admin.breaksign")) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("sign-protected"));
                }
            }
            return;
        }

        // Check if the block being broken is a support block for a rental sign
        String regionName = plugin.getSignManager().getSupportBlockRegion(blockLoc);
        if (regionName != null) {
            // This block supports a rental sign - protect it
            if (!event.getPlayer().hasPermission("regionrental.admin.breaksign")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("sign-support-protected"));
            }
        }
    }
}
