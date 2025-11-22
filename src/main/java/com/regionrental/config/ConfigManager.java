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

        // Refund tracking messages
        messages.put("refund-issued", "&aRefund issued to &e{player}&a: &e{amount}&a (Reason: {reason})");
        messages.put("refund-already-given", "&cCannot refund. Total refunded (&e{refunded}&c) would exceed total paid (&e{paid}&c).");
        messages.put("refund-history-header", "&a&lRefund History for &e{region}");
        messages.put("refund-partial", "&aProportional refund for &e{days}&a days removed: &e{amount}");

        // Duration command messages
        messages.put("duration-add-charged", "&aAdded &e{days}&a days to &e{region}&a. Player &e{player}&a was charged &e{amount}&a.");
        messages.put("duration-add-free", "&aAdded &e{days}&a to &e{region}&a (no charge).");
        messages.put("duration-remove-refunded", "&aRemoved &e{days}&a days from &e{region}&a. Player &e{player}&a was refunded &e{amount}&a.");
        messages.put("duration-remove-no-refund", "&aRemoved &e{days}&a days from &e{region}&a (no refund issued).");

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
        // Check RegionsConfig first if available
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionPrice(region, defaultPrice);
        }
        // Fallback to old config.yml method or default
        return regionPrices.getOrDefault(region, defaultPrice);
    }

    public int getDurationForRegion(String region) {
        // Check RegionsConfig first if available
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionDuration(region, defaultDuration);
        }
        // Fallback to old config.yml method or default
        return config.getInt("regions." + region + ".duration", defaultDuration);
    }

    public int getMaxExtensionsForRegion(String region) {
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionMaxExtensions(region, maxExtensions);
        }
        return maxExtensions;
    }

    public double getExtensionPriceForRegion(String region) {
        // Get extension price per day from RegionsConfig or calculate it
        double calculatedPrice = getExtensionPrice(region);
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionExtensionPrice(region, calculatedPrice);
        }
        return calculatedPrice;
    }

    public boolean getAllowExtensionsForRegion(String region) {
        boolean defaultAllow = config.getBoolean("extension.enabled", true);
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionAllowExtensions(region, defaultAllow);
        }
        return defaultAllow;
    }

    public int getExtensionDurationForRegion(String region) {
        if (plugin.getRegionsConfig() != null) {
            return plugin.getRegionsConfig().getRegionExtensionDuration(region, extensionDuration);
        }
        return extensionDuration;
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
    public boolean isBlockStorage() { return config.getBoolean("storage.store-player-blocks", true); }
    public List<String> getBlockBlacklist() { return config.getStringList("storage.block-blacklist"); }
    public boolean isSignProtection() { return signProtection; }
    public Map<String, Double> getPermissionPrices() { return permissionPrices; }

    // Duration-related refund and charge settings
    public boolean isRefundOnTimeRemoval() { return config.getBoolean("duration.refund-on-time-removal", true); }
    public boolean isChargeForDurationAdd() { return config.getBoolean("duration.charge-for-add", false); }
    public boolean isDurationAddBypassExtensionLimit() { return config.getBoolean("duration.add-bypass-extension-limit", true); }
    public double getDurationAddPricePerDay() { return config.getDouble("duration.add-price-per-day", 0.0); }

    // Extension price calculation helper
    public double getExtensionPrice(String region) {
        // If custom price per day is configured, use it
        double pricePerDay = getDurationAddPricePerDay();
        if (pricePerDay > 0) {
            return pricePerDay;
        }

        // Otherwise, calculate from region price and duration
        double regionPrice = getPriceForRegion(region);
        int duration = getDurationForRegion(region);

        if (duration <= 0) {
            return regionPrice; // Fallback to full price
        }

        // Return price per day
        return regionPrice / duration;
    }

    // Region verification settings
    public boolean isAutoVerifyRegions() { return config.getBoolean("regions-config.auto-verify-regions", true); }
    public boolean isEnableVerifyCommand() { return config.getBoolean("regions-config.enable-verify-command", true); }
}
