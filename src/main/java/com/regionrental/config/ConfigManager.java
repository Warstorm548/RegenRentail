package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    
    private final RegionRental plugin;
    private FileConfiguration config;
    
    // Cached values
    private String prefix;
    private boolean debug;
    private int expirationCheckInterval;
    
    // Economy settings
    private boolean economyEnabled;
    private double defaultPrice;
    private String currencyFormat;
    private Map<String, Double> regionPrices;
    private Map<String, Double> permissionPrices;
    
    // Durations
    private int defaultDuration;
    private int extensionDuration;
    private int maxExtensions;
    private List<Integer> availableDurations;
    
    // Limits
    private int maxRentalsPerPlayer;
    
    // Messages
    private Map<String, String> messages;
    
    // Sign formats
    private List<String> availableSignFormat;
    private List<String> rentedSignFormat;
    
    // Features
    private boolean blockRestoration;
    private boolean autoDeleteSchematics;
    private boolean itemStorage;
    private boolean signProtection;
    
    public ConfigManager(RegionRental plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadConfig();
    }
    
    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadConfig();
    }
    
    private void loadConfig() {
        // General settings
        prefix = color(config.getString("general.prefix", "&8[&6RegionRental&8]&r "));
        debug = config.getBoolean("general.debug", false);
        expirationCheckInterval = config.getInt("general.expiration-check-interval", 1);
        
        // Economy settings
        economyEnabled = config.getBoolean("economy.enabled", true);
        defaultPrice = config.getDouble("economy.default-price", 100.0);
        currencyFormat = config.getString("economy.currency-format", "$%.2f");
        
        // Load per-region prices
        regionPrices = new HashMap<>();
        if (config.contains("regions")) {
            for (String region : config.getConfigurationSection("regions").getKeys(false)) {
                double price = config.getDouble("regions." + region + ".price", defaultPrice);
                regionPrices.put(region, price);
            }
        }
        
        // Load permission-based prices
        permissionPrices = new HashMap<>();
        if (config.contains("permission-prices")) {
            for (String perm : config.getConfigurationSection("permission-prices").getKeys(false)) {
                double price = config.getDouble("permission-prices." + perm, defaultPrice);
                permissionPrices.put(perm, price);
            }
        }
        
        // Duration settings
        defaultDuration = config.getInt("durations.default-days", 7);
        extensionDuration = config.getInt("extension.extension-days", 7);
        maxExtensions = config.getInt("extension.max-extensions", 10);
        availableDurations = config.getIntegerList("durations.available-durations");
        if (availableDurations.isEmpty()) {
            availableDurations = List.of(1, 3, 7, 14, 30);
        }
        
        // Limits
        maxRentalsPerPlayer = config.getInt("limits.max-rentals-per-player", 3);
        
        // Load messages
        loadMessages();
        
        // Sign formats
        availableSignFormat = config.getStringList("signs.available-format");
        if (availableSignFormat.isEmpty()) {
            availableSignFormat = List.of(
                "&2[AVAILABLE]",
                "&6{region}",
                "&e{price}",
                "&7{duration} days"
            );
        }
        
        rentedSignFormat = config.getStringList("signs.rented-format");
        if (rentedSignFormat.isEmpty()) {
            rentedSignFormat = List.of(
                "&c[RENTED]",
                "&6{region}",
                "&e{owner}",
                "&7Exp: {expires}"
            );
        }
        
        // Features
        blockRestoration = config.getBoolean("restoration.enabled", true);
        autoDeleteSchematics = config.getBoolean("restoration.auto-delete-schematics", true);
        itemStorage = config.getBoolean("storage.enabled", true);
        signProtection = config.getBoolean("signs.protect-signs", true);
    }
    
    private void loadMessages() {
        messages = new HashMap<>();
        
        // Load default messages
        messages.put("no-permission", "&cYou don't have permission to do that!");
        messages.put("region-not-found", "&cRegion &e{region}&c not found!");
        messages.put("already-rented", "&cThis region is already rented!");
        messages.put("rental-success", "&aYou have successfully rented &e{region}&a for &e{days}&a days!");
        messages.put("rental-expired", "&cYour rental of &e{region}&c has expired!");
        messages.put("not-enough-money", "&cYou don't have enough money! Need &e{amount}");
        messages.put("max-rentals-reached", "&cYou have reached the maximum number of rentals!");
        messages.put("rental-extended", "&aRental extended for &e{days}&a days!");
        messages.put("max-extensions-reached", "&cMaximum extensions reached for this rental!");
        messages.put("items-stored", "&aYour items from &e{region}&a have been stored!");
        messages.put("items-retrieved", "&aYou have retrieved your stored items!");
        messages.put("no-stored-items", "&cYou have no stored items!");
        messages.put("sign-created", "&aRental sign created for region &e{region}!");
        messages.put("sign-removed", "&aRental sign removed!");
        messages.put("rental-reset", "&aRental for &e{region}&a has been reset!");
        messages.put("admin-reset-success", "&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.");
        messages.put("rental-reset-refund", "&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.");
        messages.put("region-removed", "&aRegionRental setup completely removed from &e{region}&a:");
        messages.put("rental-info", "&6=== Rental Info for {region} ===");
        messages.put("config-reloaded", "&aConfiguration reloaded!");
        
        // Override with config values
        if (config.contains("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, config.getString("messages." + key));
            }
        }
    }
    
    public String getMessage(String key, String... replacements) {
        String message = messages.getOrDefault(key, "&cMissing message: " + key);
        message = prefix + message;
        
        // Replace placeholders
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        
        return color(message);
    }
    
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    public double getPriceForRegion(String region) {
        return regionPrices.getOrDefault(region, defaultPrice);
    }
    
    public int getDurationForRegion(String region) {
        return config.getInt("regions." + region + ".duration", defaultDuration);
    }
    
    // Getters
    public String getPrefix() { return prefix; }
    public boolean isDebug() { return debug; }
    public int getExpirationCheckInterval() { return expirationCheckInterval; }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public double getDefaultPrice() { return defaultPrice; }
    public String getCurrencyFormat() { return currencyFormat; }
    public int getDefaultDuration() { return defaultDuration; }
    public int getExtensionDuration() { return extensionDuration; }
    public int getMaxExtensions() { return maxExtensions; }
    public List<Integer> getAvailableDurations() { return availableDurations; }
    public int getMaxRentalsPerPlayer() { return maxRentalsPerPlayer; }
    public boolean isRefundOnDurationReset() { return config.getBoolean("extension.refund-on-duration-reset", true); }
    public List<String> getAvailableSignFormat() { return availableSignFormat; }
    public List<String> getRentedSignFormat() { return rentedSignFormat; }
    public boolean isBlockRestoration() { return blockRestoration; }
    public boolean isAutoDeleteSchematics() { return autoDeleteSchematics; }
    public boolean isItemStorage() { return itemStorage; }
    public boolean isSignProtection() { return signProtection; }
    public Map<String, Double> getPermissionPrices() { return permissionPrices; }
}
