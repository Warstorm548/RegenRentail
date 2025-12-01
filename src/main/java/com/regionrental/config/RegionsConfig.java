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
                    "Format: world:region (e.g., world:shop1, world_nether:shop2)\n" +
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

        // Run migration to world-aware keys
        migrateToWorldAwareKeys();
    }

    /**
     * Migrate regions from old format (region name only) to new format (world:region)
     * This runs automatically on plugin load and is idempotent (safe to run multiple times)
     */
    private void migrateToWorldAwareKeys() {
        if (!config.contains("regions")) {
            return;
        }

        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        if (regionsSection == null) {
            return;
        }

        String defaultWorld = plugin.getServer().getWorlds().get(0).getName();
        Map<String, ConfigurationSection> migrations = new HashMap<>();
        int migratedCount = 0;

        // Find old format entries (no ":" in key)
        for (String key : regionsSection.getKeys(false)) {
            if (!key.contains(":")) {
                // Old format - default to first world
                String newKey = defaultWorld + ":" + key;

                // Copy all data to new key
                ConfigurationSection oldSection = config.getConfigurationSection("regions." + key);
                migrations.put(newKey, oldSection);

                migratedCount++;
            }
        }

        // Apply migrations
        if (!migrations.isEmpty()) {
            for (Map.Entry<String, ConfigurationSection> entry : migrations.entrySet()) {
                String compositeKey = entry.getKey();
                ConfigurationSection data = entry.getValue();

                // Copy all data from old section to new key
                for (String subKey : data.getKeys(true)) {
                    Object value = data.get(subKey);
                    config.set("regions." + compositeKey + "." + subKey, value);
                }
            }

            // Remove old keys (those without ":")
            for (String key : new HashSet<>(regionsSection.getKeys(false))) {
                if (!key.contains(":")) {
                    config.set("regions." + key, null);
                }
            }

            save();
            plugin.getLogger().info("Migrated " + migratedCount + " region override(s) to world-aware format (defaulted to world: " + defaultWorld + ")");
        }
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
    public void removeRegion(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        if (!hasRegion(region, world)) {
            return;
        }

        config.set("regions." + compositeKey, null);
        save();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Removed region '" + compositeKey + "' from regions.yml");
        }
    }

    /**
     * Check if region exists in config
     */
    public boolean hasRegion(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        return config.contains("regions." + compositeKey);
    }

    /**
     * Get region price or fallback to default
     * Lookup order: group override → region override → default
     */
    public double getRegionPrice(String region, org.bukkit.World world, double defaultPrice) {
        String compositeKey = world.getName() + ":" + region;

        // Check if region is in a group and get group override
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null) {
                double groupPrice = getGroupPrice(groupName, defaultPrice);
                // Only use group override if it's actually set (not the default fallback)
                String groupPath = "groups." + groupName + ".price";
                if (config.contains(groupPath)) {
                    return groupPrice;
                }
            }
        }

        // Check region-specific override
        String path = "regions." + compositeKey + ".price";
        if (config.contains(path)) {
            return config.getDouble(path, defaultPrice);
        }

        return defaultPrice;
    }

    /**
     * Get region duration or fallback to default
     * Lookup order: group override → region override → default
     */
    public int getRegionDuration(String region, org.bukkit.World world, int defaultDuration) {
        String compositeKey = world.getName() + ":" + region;

        // Check group override first
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null && config.contains("groups." + groupName + ".duration")) {
                return getGroupDuration(groupName, defaultDuration);
            }
        }

        // Check region override
        String path = "regions." + compositeKey + ".duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }

        return defaultDuration;
    }

    /**
     * Get region max extensions or fallback to default
     * Lookup order: group override → region override → default
     */
    public int getRegionMaxExtensions(String region, org.bukkit.World world, int defaultMax) {
        String compositeKey = world.getName() + ":" + region;

        // Check group override first
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null && config.contains("groups." + groupName + ".max-extensions")) {
                return getGroupMaxExtensions(groupName, defaultMax);
            }
        }

        // Check region override
        String path = "regions." + compositeKey + ".max-extensions";
        if (config.contains(path)) {
            return config.getInt(path, defaultMax);
        }

        return defaultMax;
    }

    /**
     * Get region extension price or fallback to default
     * Lookup order: group override → region override → default
     */
    public double getRegionExtensionPrice(String region, org.bukkit.World world, double defaultPrice) {
        String compositeKey = world.getName() + ":" + region;

        // Check group override first
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null && config.contains("groups." + groupName + ".extension-price")) {
                double price = getGroupExtensionPrice(groupName, defaultPrice);
                return price > 0 ? price : defaultPrice;
            }
        }

        // Check region override
        String path = "regions." + compositeKey + ".extension-price";
        if (config.contains(path)) {
            double price = config.getDouble(path, defaultPrice);
            return price > 0 ? price : defaultPrice;
        }

        return defaultPrice;
    }

    /**
     * Get region allow extensions or fallback to default
     * Lookup order: group override → region override → default
     */
    public boolean getRegionAllowExtensions(String region, org.bukkit.World world, boolean defaultAllow) {
        String compositeKey = world.getName() + ":" + region;

        // Check group override first
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null && config.contains("groups." + groupName + ".allow-extensions")) {
                return getGroupAllowExtensions(groupName, defaultAllow);
            }
        }

        // Check region override
        String path = "regions." + compositeKey + ".allow-extensions";
        if (config.contains(path)) {
            return config.getBoolean(path, defaultAllow);
        }

        return defaultAllow;
    }

    /**
     * Get region extension duration or fallback to default
     * Lookup order: group override → region override → default
     */
    public int getRegionExtensionDuration(String region, org.bukkit.World world, int defaultDuration) {
        String compositeKey = world.getName() + ":" + region;

        // Check group override first
        if (plugin.getGroupsConfig() != null && plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            if (groupName != null && config.contains("groups." + groupName + ".extension-duration")) {
                return getGroupExtensionDuration(groupName, defaultDuration);
            }
        }

        // Check region override
        String path = "regions." + compositeKey + ".extension-duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }

        return defaultDuration;
    }

    /**
     * Set region price override
     */
    public void setRegionPrice(String region, org.bukkit.World world, double price) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".price";
        config.set(path, price);
        save();
    }

    /**
     * Set region duration override
     */
    public void setRegionDuration(String region, org.bukkit.World world, int days) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".duration";
        config.set(path, days);
        save();
    }

    /**
     * Set region max extensions override
     */
    public void setRegionMaxExtensions(String region, org.bukkit.World world, int maxExtensions) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".max-extensions";
        config.set(path, maxExtensions);
        save();
    }

    /**
     * Set region extension price override
     */
    public void setRegionExtensionPrice(String region, org.bukkit.World world, double price) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".extension-price";
        config.set(path, price);
        save();
    }

    /**
     * Set region allow extensions override
     */
    public void setRegionAllowExtensions(String region, org.bukkit.World world, boolean allow) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".allow-extensions";
        config.set(path, allow);
        save();
    }

    /**
     * Set region extension duration override
     */
    public void setRegionExtensionDuration(String region, org.bukkit.World world, int days) {
        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey + ".extension-duration";
        config.set(path, days);
        save();
    }

    /**
     * Get all override settings for a specific region
     * Returns empty map if region has no overrides
     */
    public Map<String, Object> getRegionOverrides(String region, org.bukkit.World world) {
        Map<String, Object> overrides = new HashMap<>();

        if (!hasRegion(region, world)) {
            return overrides;
        }

        String compositeKey = world.getName() + ":" + region;
        String path = "regions." + compositeKey;
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

        // Default to first world for migration (old format had no world awareness)
        String defaultWorld = plugin.getServer().getWorlds().get(0).getName();
        org.bukkit.World world = plugin.getServer().getWorld(defaultWorld);

        for (String region : regionKeys) {
            // Skip if already in regions.yml (check with default world)
            if (hasRegion(region, world)) {
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

    // ========== Group Override Methods ==========

    /**
     * Set group price override
     */
    public void setGroupPrice(String groupName, double price) {
        String path = "groups." + groupName + ".price";
        config.set(path, price);
        save();
    }

    /**
     * Set group duration override
     */
    public void setGroupDuration(String groupName, int days) {
        String path = "groups." + groupName + ".duration";
        config.set(path, days);
        save();
    }

    /**
     * Set group max extensions override
     */
    public void setGroupMaxExtensions(String groupName, int maxExtensions) {
        String path = "groups." + groupName + ".max-extensions";
        config.set(path, maxExtensions);
        save();
    }

    /**
     * Set group extension price override
     */
    public void setGroupExtensionPrice(String groupName, double price) {
        String path = "groups." + groupName + ".extension-price";
        config.set(path, price);
        save();
    }

    /**
     * Set group allow extensions override
     */
    public void setGroupAllowExtensions(String groupName, boolean allow) {
        String path = "groups." + groupName + ".allow-extensions";
        config.set(path, allow);
        save();
    }

    /**
     * Set group extension duration override
     */
    public void setGroupExtensionDuration(String groupName, int days) {
        String path = "groups." + groupName + ".extension-duration";
        config.set(path, days);
        save();
    }

    /**
     * Get group price or fallback to default
     */
    public double getGroupPrice(String groupName, double defaultPrice) {
        String path = "groups." + groupName + ".price";
        if (config.contains(path)) {
            return config.getDouble(path, defaultPrice);
        }
        return defaultPrice;
    }

    /**
     * Get group duration or fallback to default
     */
    public int getGroupDuration(String groupName, int defaultDuration) {
        String path = "groups." + groupName + ".duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Get group max extensions or fallback to default
     */
    public int getGroupMaxExtensions(String groupName, int defaultMax) {
        String path = "groups." + groupName + ".max-extensions";
        if (config.contains(path)) {
            return config.getInt(path, defaultMax);
        }
        return defaultMax;
    }

    /**
     * Get group extension price or fallback to default
     */
    public double getGroupExtensionPrice(String groupName, double defaultPrice) {
        String path = "groups." + groupName + ".extension-price";
        if (config.contains(path)) {
            double price = config.getDouble(path, defaultPrice);
            // If set to 0, use calculated default
            return price > 0 ? price : defaultPrice;
        }
        return defaultPrice;
    }

    /**
     * Get group allow extensions or fallback to default
     */
    public boolean getGroupAllowExtensions(String groupName, boolean defaultAllow) {
        String path = "groups." + groupName + ".allow-extensions";
        if (config.contains(path)) {
            return config.getBoolean(path, defaultAllow);
        }
        return defaultAllow;
    }

    /**
     * Get group extension duration or fallback to default
     */
    public int getGroupExtensionDuration(String groupName, int defaultDuration) {
        String path = "groups." + groupName + ".extension-duration";
        if (config.contains(path)) {
            return config.getInt(path, defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Check if group has any overrides configured
     */
    public boolean hasGroupOverrides(String groupName) {
        return config.contains("groups." + groupName);
    }

    /**
     * Get all override settings for a specific group
     * Returns empty map if group has no overrides
     */
    public Map<String, Object> getGroupOverrides(String groupName) {
        Map<String, Object> overrides = new HashMap<>();

        if (!hasGroupOverrides(groupName)) {
            return overrides;
        }

        String path = "groups." + groupName;
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) {
            return overrides;
        }

        // Get all keys and values for this group
        for (String key : section.getKeys(false)) {
            overrides.put(key, section.get(key));
        }

        return overrides;
    }

    /**
     * Remove all overrides for a group
     * Called when group is deleted to ensure clean state
     */
    public void removeGroupOverrides(String groupName) {
        if (!hasGroupOverrides(groupName)) {
            return;
        }

        config.set("groups." + groupName, null);
        save();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Removed overrides for group '" + groupName + "' from regions.yml");
        }
    }

    /**
     * Get all groups with overrides
     */
    public Set<String> getAllGroupsWithOverrides() {
        if (!config.contains("groups")) {
            return new HashSet<>();
        }

        ConfigurationSection section = config.getConfigurationSection("groups");
        if (section == null) {
            return new HashSet<>();
        }

        return section.getKeys(false);
    }

    /**
     * Check if a region has individual overrides
     * Used to detect conflicts when adding regions to groups
     *
     * @param compositeKey The composite key (world:region)
     * @return true if the region has any individual overrides
     */
    public boolean hasRegionOverrides(String compositeKey) {
        String path = "regions." + compositeKey;
        return config.contains(path) && config.isConfigurationSection(path);
    }

    /**
     * Remove all individual overrides for a region
     * Called when adding region to a group to prevent conflicts
     *
     * @param compositeKey The composite key (world:region)
     */
    public void removeRegionOverrides(String compositeKey) {
        if (!hasRegionOverrides(compositeKey)) {
            return;
        }

        config.set("regions." + compositeKey, null);
        save();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Removed individual overrides for region '" + compositeKey + "' from regions.yml");
        }
    }
}
