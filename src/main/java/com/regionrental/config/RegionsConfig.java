package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class RegionsConfig {

    private final RegionRental plugin;
    private File configFile;
    private FileConfiguration config;

    public RegionsConfig(RegionRental plugin) {
        this.plugin = plugin;
        createConfig();
    }

    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "regions.yml");

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();

                // Create file with header
                config = YamlConfiguration.loadConfiguration(configFile);
                config.options().header(
                    "RegionRental - Per-Region Configuration\n" +
                    "This file is automatically populated when you create rental signs.\n" +
                    "Only override the settings you want to change - all others use defaults from config.yml\n" +
                    "\n" +
                    "Available settings per region:\n" +
                    "  price: <amount>              # Rental price (default from config.yml)\n" +
                    "  duration: <days>             # Rental duration in days (default from config.yml)\n" +
                    "  max-extensions: <number>     # Maximum extensions allowed (default from config.yml)\n" +
                    "  extension-price: <amount>    # Price per extension day (default: auto-calculated)\n" +
                    "  allow-extensions: <true|false> # Allow extensions (default from config.yml)\n" +
                    "  extension-duration: <days>   # Extension duration in days (default from config.yml)\n"
                );
                config.createSection("regions");
                save();

                plugin.getLogger().info("Created regions.yml file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create regions.yml!", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save regions.yml!", e);
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Add a region with commented template
     */
    public void addRegion(String region) {
        if (hasRegion(region)) {
            return; // Already exists, don't overwrite
        }

        String path = "regions." + region;

        // Add commented template
        List<String> comments = Arrays.asList(
            "Uncomment and modify settings below to override defaults",
            "price: 100.0           # Default from config.yml",
            "duration: 7            # Default from config.yml",
            "max-extensions: 10     # Default from config.yml",
            "extension-price: 0.0   # Auto-calculated if 0",
            "allow-extensions: true # Default from config.yml",
            "extension-duration: 7  # Default from config.yml"
        );

        // Create empty section
        config.createSection(path);
        config.set(path + "._comment", comments);

        save();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Added region '" + region + "' to regions.yml with default template");
        }
    }

    /**
     * Remove a region entry
     */
    public void removeRegion(String region) {
        if (!hasRegion(region)) {
            return;
        }

        config.set("regions." + region, null);
        save();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Removed region '" + region + "' from regions.yml");
        }
    }

    /**
     * Check if region exists in config
     */
    public boolean hasRegion(String region) {
        return config.contains("regions." + region);
    }

    /**
     * Get region price or fallback to default
     */
    public double getRegionPrice(String region, double defaultPrice) {
        String path = "regions." + region + ".price";
        if (config.contains(path)) {
            return config.getDouble(path, defaultPrice);
        }
        return defaultPrice;
    }

    /**
     * Get region duration or fallback to default
     */
    public int getRegionDuration(String region, int defaultDuration) {
        String path = "regions." + region + ".duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Get region max extensions or fallback to default
     */
    public int getRegionMaxExtensions(String region, int defaultMax) {
        String path = "regions." + region + ".max-extensions";
        if (config.contains(path)) {
            return config.getInt(path, defaultMax);
        }
        return defaultMax;
    }

    /**
     * Get region extension price or fallback to default
     */
    public double getRegionExtensionPrice(String region, double defaultPrice) {
        String path = "regions." + region + ".extension-price";
        if (config.contains(path)) {
            double price = config.getDouble(path, defaultPrice);
            // If set to 0, use calculated default
            return price > 0 ? price : defaultPrice;
        }
        return defaultPrice;
    }

    /**
     * Get region allow extensions or fallback to default
     */
    public boolean getRegionAllowExtensions(String region, boolean defaultAllow) {
        String path = "regions." + region + ".allow-extensions";
        if (config.contains(path)) {
            return config.getBoolean(path, defaultAllow);
        }
        return defaultAllow;
    }

    /**
     * Get region extension duration or fallback to default
     */
    public int getRegionExtensionDuration(String region, int defaultDuration) {
        String path = "regions." + region + ".extension-duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Get all configured regions
     */
    public Set<String> getAllRegions() {
        if (!config.contains("regions")) {
            return new HashSet<>();
        }

        ConfigurationSection section = config.getConfigurationSection("regions");
        if (section == null) {
            return new HashSet<>();
        }

        return section.getKeys(false);
    }

    /**
     * Migrate regions from main config.yml and remove them from config.yml
     */
    public void migrateFromMainConfig(FileConfiguration mainConfig) {
        if (!mainConfig.contains("regions")) {
            return;
        }

        ConfigurationSection regionsSection = mainConfig.getConfigurationSection("regions");
        if (regionsSection == null) {
            return;
        }

        Set<String> regionKeys = regionsSection.getKeys(false);
        if (regionKeys.isEmpty()) {
            return;
        }

        int migratedCount = 0;

        for (String region : regionKeys) {
            // Skip if already in regions.yml
            if (hasRegion(region)) {
                continue;
            }

            String sourcePath = "regions." + region;
            String destPath = "regions." + region;

            // Copy all settings
            if (mainConfig.contains(sourcePath + ".price")) {
                config.set(destPath + ".price", mainConfig.getDouble(sourcePath + ".price"));
            }
            if (mainConfig.contains(sourcePath + ".duration")) {
                config.set(destPath + ".duration", mainConfig.getInt(sourcePath + ".duration"));
            }
            if (mainConfig.contains(sourcePath + ".max-extensions")) {
                config.set(destPath + ".max-extensions", mainConfig.getInt(sourcePath + ".max-extensions"));
            }
            if (mainConfig.contains(sourcePath + ".extension-price")) {
                config.set(destPath + ".extension-price", mainConfig.getDouble(sourcePath + ".extension-price"));
            }
            if (mainConfig.contains(sourcePath + ".allow-extensions")) {
                config.set(destPath + ".allow-extensions", mainConfig.getBoolean(sourcePath + ".allow-extensions"));
            }
            if (mainConfig.contains(sourcePath + ".extension-duration")) {
                config.set(destPath + ".extension-duration", mainConfig.getInt(sourcePath + ".extension-duration"));
            }

            migratedCount++;
        }

        if (migratedCount > 0) {
            save();
            plugin.getLogger().info("Migrated " + migratedCount + " region(s) from config.yml to regions.yml");

            // Remove from config.yml (option B)
            mainConfig.set("regions", null);
            try {
                mainConfig.save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.getLogger().info("Removed migrated regions from config.yml");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not remove regions from config.yml", e);
            }
        }
    }

    /**
     * Verify regions.yml has entries for all regions with signs
     * Re-adds missing regions with template
     */
    public void verifyAndRepairRegions() {
        // Get all regions that have rental signs
        Map<String, org.bukkit.Location> allSigns = plugin.getSignsConfig().getAllSigns();

        if (allSigns.isEmpty()) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Region verification: No rental signs found");
            }
            return;
        }

        List<String> reAddedRegions = new ArrayList<>();
        List<String> orphanedConfigs = new ArrayList<>();

        // Check for missing region configs (sign exists but no config)
        for (String regionName : allSigns.keySet()) {
            if (!hasRegion(regionName)) {
                addRegion(regionName);  // Add with default commented template
                reAddedRegions.add(regionName);
            }
        }

        // Check for orphaned configs (config exists but no sign) - debug only
        if (plugin.getConfigManager().isDebug()) {
            Set<String> configuredRegions = getAllRegions();
            for (String regionName : configuredRegions) {
                if (!allSigns.containsKey(regionName)) {
                    orphanedConfigs.add(regionName);
                }
            }
        }

        // Log results
        if (!reAddedRegions.isEmpty()) {
            plugin.getLogger().warning("Region verification: Found " + reAddedRegions.size() +
                " region(s) missing from regions.yml - re-added with defaults:");
            for (String region : reAddedRegions) {
                plugin.getLogger().warning("  - " + region);
            }
        } else if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Region verification: All regions have configuration entries");
        }

        if (!orphanedConfigs.isEmpty() && plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Region verification: Found " + orphanedConfigs.size() +
                " orphaned config(s) (no sign):");
            for (String region : orphanedConfigs) {
                plugin.getLogger().info("  - " + region);
            }
            plugin.getLogger().info("These can be safely removed or will be used when signs are created");
        }
    }

    /**
     * Get verification report for /rrverify command
     */
    public Map<String, Object> getVerificationReport() {
        Map<String, Object> report = new HashMap<>();

        // Get all regions with signs
        Map<String, org.bukkit.Location> allSigns = plugin.getSignsConfig().getAllSigns();
        Set<String> regionsWithSigns = allSigns.keySet();

        // Get all configured regions
        Set<String> configuredRegions = getAllRegions();

        // Find missing configs
        List<String> missingConfigs = new ArrayList<>();
        for (String region : regionsWithSigns) {
            if (!configuredRegions.contains(region)) {
                missingConfigs.add(region);
            }
        }

        // Find orphaned configs
        List<String> orphanedConfigs = new ArrayList<>();
        for (String region : configuredRegions) {
            if (!regionsWithSigns.contains(region)) {
                orphanedConfigs.add(region);
            }
        }

        report.put("totalSigns", regionsWithSigns.size());
        report.put("totalConfigs", configuredRegions.size());
        report.put("missingConfigs", missingConfigs);
        report.put("orphanedConfigs", orphanedConfigs);
        report.put("regionsWithSigns", new ArrayList<>(regionsWithSigns));
        report.put("configuredRegions", new ArrayList<>(configuredRegions));

        return report;
    }
}
