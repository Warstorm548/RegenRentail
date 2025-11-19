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
                    "Use /rroverride commands to set custom values for specific regions.\n" +
                    "Regions not listed here will use default settings from config.yml\n" +
                    "\n" +
                    "Commands:\n" +
                    "  /rroverride price <region> <amount>\n" +
                    "  /rroverride duration <region> <days>\n" +
                    "  /rroverride maxextensions <region> <count>\n" +
                    "  /rroverride extensionprice <region> <amount>\n" +
                    "  /rroverride allowextensions <region> <true|false>\n" +
                    "  /rroverride extensionduration <region> <days>\n" +
                    "  /rroverride remove <region>              # Remove all overrides\n" +
                    "  /rroverride list [region]                # View overrides\n"
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
     * Set region price override
     */
    public void setRegionPrice(String region, double price) {
        String path = "regions." + region + ".price";
        config.set(path, price);
        save();
    }

    /**
     * Set region duration override
     */
    public void setRegionDuration(String region, int days) {
        String path = "regions." + region + ".duration";
        config.set(path, days);
        save();
    }

    /**
     * Set region max extensions override
     */
    public void setRegionMaxExtensions(String region, int maxExtensions) {
        String path = "regions." + region + ".max-extensions";
        config.set(path, maxExtensions);
        save();
    }

    /**
     * Set region extension price override
     */
    public void setRegionExtensionPrice(String region, double price) {
        String path = "regions." + region + ".extension-price";
        config.set(path, price);
        save();
    }

    /**
     * Set region allow extensions override
     */
    public void setRegionAllowExtensions(String region, boolean allow) {
        String path = "regions." + region + ".allow-extensions";
        config.set(path, allow);
        save();
    }

    /**
     * Set region extension duration override
     */
    public void setRegionExtensionDuration(String region, int days) {
        String path = "regions." + region + ".extension-duration";
        config.set(path, days);
        save();
    }

    /**
     * Get all override settings for a specific region
     * Returns empty map if region has no overrides
     */
    public Map<String, Object> getRegionOverrides(String region) {
        Map<String, Object> overrides = new HashMap<>();

        if (!hasRegion(region)) {
            return overrides;
        }

        String path = "regions." + region;
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) {
            return overrides;
        }

        // Get all keys and values for this region
        for (String key : section.getKeys(false)) {
            overrides.put(key, section.get(key));
        }

        return overrides;
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
     * Verify regions.yml - reports on orphaned configs (configs without signs)
     * Note: Regions without configs will use defaults from config.yml
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

        Set<String> configuredRegions = getAllRegions();
        List<String> orphanedConfigs = new ArrayList<>();

        // Check for orphaned configs (config exists but no sign)
        for (String regionName : configuredRegions) {
            if (!allSigns.containsKey(regionName)) {
                orphanedConfigs.add(regionName);
            }
        }

        // Log results
        if (!orphanedConfigs.isEmpty()) {
            plugin.getLogger().info("Region verification: Found " + orphanedConfigs.size() +
                " orphaned config(s) (custom overrides exist but no sign):");
            for (String region : orphanedConfigs) {
                plugin.getLogger().info("  - " + region);
            }
            plugin.getLogger().info("These configs are safe but may be unused. Use /rroverride remove <region> to clean up.");
        } else if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Region verification: No orphaned configs found");
        }

        // Count regions using defaults
        int usingDefaults = allSigns.size() - configuredRegions.size();
        if (usingDefaults > 0 && plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Region verification: " + usingDefaults + " region(s) using default settings");
            plugin.getLogger().info("Use /rroverride commands to set custom values for specific regions");
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
