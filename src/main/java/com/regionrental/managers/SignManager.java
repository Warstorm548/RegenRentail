package com.regionrental.managers;

import com.regionrental.RegionRental;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import java.util.Map;

public class SignManager {
    
    private final RegionRental plugin;
    
    public SignManager(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    public void loadAllSigns() {
        // Signs are loaded from SignsConfig on demand
        updateAllSigns();
    }
    
    public void createSign(String regionName, Location location) {
        // Store sign location
        plugin.getSignsConfig().addSign(regionName, location);
        
        // Update the sign
        updateSign(regionName);
        
        plugin.getLogger().info("Created rental sign for region " + regionName);
    }
    
    public void removeSign(String regionName) {
        plugin.getSignsConfig().removeSign(regionName);
        plugin.getLogger().info("Removed rental sign for region " + regionName);
    }

    /**
     * Completely removes RegionRental setup from a region
     * This includes removing the sign from config and clearing the physical sign
     * @param regionName The region to remove setup from
     * @return true if sign was removed, false if no sign existed
     */
    public boolean removeRegionSetup(String regionName) {
        Location location = plugin.getSignsConfig().getSignLocation(regionName);

        if (location == null) {
            return false; // No sign exists for this region
        }

        // Clear the physical sign if it exists
        Block block = location.getBlock();
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();

            // Clear all lines on the sign
            for (int i = 0; i < 4; i++) {
                sign.line(i, net.kyori.adventure.text.Component.text(""));
            }
            sign.update(true);

            // Optionally break the sign block (uncomment if desired)
            // block.setType(Material.AIR);
        }

        // Remove from config
        plugin.getSignsConfig().removeSign(regionName);
        plugin.getLogger().info("Removed RegionRental setup from region " + regionName);

        return true;
    }
    
    public void updateSign(String regionName) {
        Location location = plugin.getSignsConfig().getSignLocation(regionName);
        
        if (location == null) {
            return;
        }
        
        Block block = location.getBlock();
        
        // Check if block is a sign
        if (!(block.getState() instanceof Sign)) {
            plugin.getLogger().warning("Sign location for " + regionName + " is not a sign!");
            return;
        }
        
        Sign sign = (Sign) block.getState();
        Rental rental = plugin.getRentalManager().getRental(regionName);
        
        if (rental == null) {
            // Region is available
            updateAvailableSign(sign, regionName);
        } else {
            // Region is rented
            updateRentedSign(sign, rental);
        }
        
        sign.update(true);
    }
    
    private void updateAvailableSign(Sign sign, String regionName) {
        double price = plugin.getConfigManager().getPriceForRegion(regionName);
        int duration = plugin.getConfigManager().getDurationForRegion(regionName);
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
    
    public void updateAllSigns() {
        Map<String, Location> signs = plugin.getSignsConfig().getAllSigns();
        
        for (String regionName : signs.keySet()) {
            updateSign(regionName);
        }
    }
    
    public boolean isRentalSign(Location location) {
        return plugin.getSignsConfig().getRegionByLocation(location) != null;
    }
    
    public String getRegionFromSign(Location location) {
        return plugin.getSignsConfig().getRegionByLocation(location);
    }
    
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
