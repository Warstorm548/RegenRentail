package com.regionrental.managers;

import com.regionrental.RegionRental;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RentalManager {
    
    private final RegionRental plugin;
    private final Map<String, Rental> rentals;
    private File rentalsFile;
    private FileConfiguration rentalsConfig;
    
    public RentalManager(RegionRental plugin) {
        this.plugin = plugin;
        this.rentals = new ConcurrentHashMap<>();
        setupRentalsFile();
    }
    
    private void setupRentalsFile() {
        rentalsFile = new File(plugin.getDataFolder(), "rentals.yml");
        
        if (!rentalsFile.exists()) {
            rentalsFile.getParentFile().mkdirs();
            try {
                rentalsFile.createNewFile();
                plugin.getLogger().info("Created rentals.yml file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create rentals.yml!", e);
            }
        }
        
        rentalsConfig = YamlConfiguration.loadConfiguration(rentalsFile);
    }
    
    public void loadAllRentals() {
        rentals.clear();
        
        if (!rentalsConfig.contains("rentals")) {
            return;
        }
        
        for (String regionName : rentalsConfig.getConfigurationSection("rentals").getKeys(false)) {
            String path = "rentals." + regionName;
            
            try {
                UUID playerUUID = UUID.fromString(rentalsConfig.getString(path + ".player-uuid"));
                String playerName = rentalsConfig.getString(path + ".player-name");
                long startDate = rentalsConfig.getLong(path + ".start-date");
                long endDate = rentalsConfig.getLong(path + ".end-date");
                int extensionCount = rentalsConfig.getInt(path + ".extension-count", 0);
                double totalPaid = rentalsConfig.getDouble(path + ".total-paid", 0);
                
                Rental rental = new Rental(regionName, playerUUID, playerName, startDate, endDate, extensionCount, totalPaid);
                rentals.put(regionName, rental);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load rental for region " + regionName + ": " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + rentals.size() + " rentals");
    }
    
    public void saveAllRentals() {
        // Clear existing data
        rentalsConfig = new YamlConfiguration();
        
        for (Map.Entry<String, Rental> entry : rentals.entrySet()) {
            Rental rental = entry.getValue();
            String path = "rentals." + rental.getRegionName();
            
            rentalsConfig.set(path + ".player-uuid", rental.getPlayerUUID().toString());
            rentalsConfig.set(path + ".player-name", rental.getPlayerName());
            rentalsConfig.set(path + ".start-date", rental.getStartDate());
            rentalsConfig.set(path + ".end-date", rental.getEndDate());
            rentalsConfig.set(path + ".extension-count", rental.getExtensionCount());
            rentalsConfig.set(path + ".total-paid", rental.getTotalPaid());
        }
        
        try {
            rentalsConfig.save(rentalsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save rentals.yml!", e);
        }
    }
    
    public boolean createRental(String regionName, Player player, int days, double price) {
        // Check if region is already rented
        if (isRented(regionName)) {
            return false;
        }

        // Check player rental limit
        if (getPlayerRentals(player.getUniqueId()).size() >= plugin.getConfigManager().getMaxRentalsPerPlayer()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-rentals-reached"));
            return false;
        }

        // Capture region state with WorldEdit before renting (if enabled)
        if (plugin.getConfigManager().isBlockRestoration()) {
            boolean captured = plugin.getWorldEditManager().captureRegion(regionName);
            if (!captured && plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Failed to capture region state for: " + regionName);
            }
        }

        // Create the rental
        long endDate = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L);
        Rental rental = new Rental(regionName, player.getUniqueId(), player.getName(), endDate, price);

        rentals.put(regionName, rental);

        // Add player to region
        plugin.getWorldGuardManager().addPlayerToRegion(regionName, player.getUniqueId());

        // Update sign
        plugin.getSignManager().updateSign(regionName);

        // Save
        saveAllRentals();

        return true;
    }
    
    public boolean extendRental(String regionName, Player player, int days, double price) {
        Rental rental = rentals.get(regionName);
        
        if (rental == null || !rental.getPlayerUUID().equals(player.getUniqueId())) {
            return false;
        }
        
        // Check extension limit
        if (rental.getExtensionCount() >= plugin.getConfigManager().getMaxExtensions()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-extensions-reached"));
            return false;
        }
        
        // Extend the rental
        rental.extendRental(days, price);
        
        // Update sign
        plugin.getSignManager().updateSign(regionName);
        
        // Save
        saveAllRentals();
        
        return true;
    }
    
    public void expireRental(String regionName) {
        Rental rental = rentals.get(regionName);

        if (rental == null) {
            return;
        }

        // Remove player from region
        plugin.getWorldGuardManager().removePlayerFromRegion(regionName, rental.getPlayerUUID());

        // Store items if enabled
        if (plugin.getConfigManager().isItemStorage()) {
            plugin.getStorageManager().storeItemsFromRegion(regionName, rental.getPlayerUUID());
        }

        // Restore region blocks with WorldEdit (if enabled)
        if (plugin.getConfigManager().isBlockRestoration()) {
            boolean restored = plugin.getWorldEditManager().restoreRegion(regionName);
            if (restored) {
                plugin.getLogger().info("Restored blocks for region: " + regionName);
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Failed to restore blocks for region: " + regionName);
            }
        }

        // Remove rental
        rentals.remove(regionName);

        // Update sign
        plugin.getSignManager().updateSign(regionName);

        // Save
        saveAllRentals();

        plugin.getLogger().info("Rental expired for region " + regionName);
    }
    
    /**
     * Resets a rental with full refund (for admin use)
     * @param regionName The region to reset
     * @return A map containing refund details (playerUUID, playerName, refundAmount) or null if rental doesn't exist
     */
    public Map<String, Object> resetRentalWithRefund(String regionName) {
        Rental rental = rentals.get(regionName);

        if (rental == null) {
            return null;
        }

        // Get rental information before expiring
        UUID playerUUID = rental.getPlayerUUID();
        String playerName = rental.getPlayerName();
        double refundAmount = rental.getTotalPaid();

        // Refund the full amount to the player
        if (refundAmount > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(playerUUID), refundAmount);

            // Notify player if online
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), refundAmount);
                player.sendMessage(plugin.getConfigManager().getMessage("rental-reset-refund",
                    "{region}", regionName,
                    "{amount}", formattedAmount));
            }
        }

        // Now expire the rental (this removes player from region, stores items, restores blocks, etc.)
        expireRental(regionName);

        // Return details for admin notification
        Map<String, Object> refundDetails = new HashMap<>();
        refundDetails.put("playerUUID", playerUUID);
        refundDetails.put("playerName", playerName);
        refundDetails.put("refundAmount", refundAmount);

        return refundDetails;
    }

    /**
     * @deprecated Use resetRentalWithRefund for admin resets to ensure proper refunds
     */
    @Deprecated
    public void resetRental(String regionName) {
        expireRental(regionName);
    }
    
    public void resetRentalTime(String regionName, int days) {
        Rental rental = rentals.get(regionName);
        if (rental != null) {
            rental.resetTime(days);
            plugin.getSignManager().updateSign(regionName);
            saveAllRentals();
        }
    }
    
    public boolean isRented(String regionName) {
        return rentals.containsKey(regionName);
    }
    
    public Rental getRental(String regionName) {
        return rentals.get(regionName);
    }
    
    public List<Rental> getPlayerRentals(UUID playerUUID) {
        return rentals.values().stream()
                .filter(rental -> rental.getPlayerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }
    
    public List<Rental> getAllRentals() {
        return new ArrayList<>(rentals.values());
    }
    
    public List<Rental> getExpiredRentals() {
        return rentals.values().stream()
                .filter(Rental::isExpired)
                .collect(Collectors.toList());
    }
    
    public Map<String, Rental> getRentalsMap() {
        return new HashMap<>(rentals);
    }
}
