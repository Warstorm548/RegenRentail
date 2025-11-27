package com.regionrental.managers;

import com.regionrental.RegionRental;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class ExpirationManager {
    
    private final RegionRental plugin;
    
    // Warning thresholds in hours
    private static final int[] WARNING_HOURS = {24, 12, 6, 1};
    
    public ExpirationManager(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    public void checkExpiredRentals() {
        List<Rental> allRentals = plugin.getRentalManager().getAllRentals();
        
        for (Rental rental : allRentals) {
            if (rental.isExpired()) {
                // Expire the rental
                handleExpiration(rental);
            } else {
                // Check for warnings
                checkWarnings(rental);
            }
        }
    }
    
    private void handleExpiration(Rental rental) {
        // Notify player if online
        Player player = Bukkit.getPlayer(rental.getPlayerUUID());
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-expired",
                "{region}", rental.getRegionName()));
        }

        // Expire the rental (CRITICAL FIX: use world-aware method)
        org.bukkit.World world = plugin.getServer().getWorld(rental.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Cannot expire rental for " + rental.getRegionName() +
                " - world '" + rental.getWorldName() + "' not found!");
            return;
        }
        plugin.getRentalManager().expireRental(rental.getRegionName(), world);

        plugin.getLogger().info("Expired rental for " + rental.getWorldName() + ":" + rental.getRegionName() +
            " (Player: " + rental.getPlayerName() + ")");
    }
    
    private void checkWarnings(Rental rental) {
        int hoursRemaining = rental.getHoursRemaining();
        
        for (int warningHour : WARNING_HOURS) {
            String warningKey = "warning_" + warningHour + "h";
            
            if (hoursRemaining <= warningHour && hoursRemaining > 0) {
                if (!rental.hasWarningBeenSent(warningKey)) {
                    sendWarning(rental, warningHour);
                    rental.markWarningSent(warningKey);
                    break; // Only send one warning at a time
                }
            }
        }
    }
    
    private void sendWarning(Rental rental, int hours) {
        Player player = Bukkit.getPlayer(rental.getPlayerUUID());
        
        if (player != null && player.isOnline()) {
            String message = plugin.getConfigManager().getMessage("rental-expiring-soon",
                "{region}", rental.getRegionName(),
                "{time}", formatTime(hours));
            
            player.sendMessage(message);
            
            // Send title if configured
            if (plugin.getConfig().getBoolean("notifications.title-enabled", true)) {
                String title = "&c&lRental Expiring!";
                String subtitle = "&e" + rental.getRegionName() + " - " + formatTime(hours);
                
                player.sendTitle(
                    color(title),
                    color(subtitle),
                    10, 60, 20
                );
            }
            
            // Play sound if configured
            if (plugin.getConfig().getBoolean("notifications.sound-enabled", true)) {
                player.playSound(player.getLocation(), 
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }
    
    private String formatTime(int hours) {
        if (hours >= 24) {
            int days = hours / 24;
            return days + " day" + (days > 1 ? "s" : "");
        } else {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }
    }
    
    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
