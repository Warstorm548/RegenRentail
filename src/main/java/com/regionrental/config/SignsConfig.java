package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;

public class SignsConfig {
    
    private final RegionRental plugin;
    private File configFile;
    private FileConfiguration config;
    
    public SignsConfig(RegionRental plugin) {
        this.plugin = plugin;
        createConfig();
    }
    
    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "signs.yml");

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
                plugin.getLogger().info("Created signs.yml file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create signs.yml!", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Run migration to world-aware keys
        migrateToWorldAwareKeys();
    }

    /**
     * Migrate signs from old format (region name only) to new format (world:region)
     * This runs automatically on plugin load and is idempotent (safe to run multiple times)
     * 
     * Migration follows a safe approach:
     * 1. First verify all migrations can be performed
     * 2. Then apply migrations (copy data to new keys)
     * 3. Save the config with new data (old keys still exist as backup)
     * 4. Only remove old keys after successful save
     */
    private void migrateToWorldAwareKeys() {
        if (!config.contains("signs")) {
            return;
        }

        ConfigurationSection signsSection = config.getConfigurationSection("signs");
        if (signsSection == null) {
            return;
        }

        // Phase 1: Validate all migrations can be performed
        Map<String, Map<String, Object>> validatedMigrations = new HashMap<>();
        List<String> oldKeysToRemove = new ArrayList<>();

        for (String key : signsSection.getKeys(false)) {
            if (!key.contains(":")) {
                // Old format - verify it has a world field
                String world = config.getString("signs." + key + ".world");
                if (world == null || world.isEmpty()) {
                    plugin.getLogger().warning("Sign '" + key + "' has no world field, skipping migration");
                    continue;
                }

                String newKey = world + ":" + key;

                // Verify old section exists and copy data to a map (snapshot)
                ConfigurationSection oldSection = config.getConfigurationSection("signs." + key);
                if (oldSection == null) {
                    plugin.getLogger().warning("Sign '" + key + "' has no data section, skipping migration");
                    continue;
                }

                // Take a snapshot of all data from old section
                Map<String, Object> dataSnapshot = new HashMap<>();
                for (String subKey : oldSection.getKeys(true)) {
                    Object value = oldSection.get(subKey);
                    // Only copy leaf values, not sections
                    if (!(value instanceof ConfigurationSection)) {
                        dataSnapshot.put(subKey, value);
                    }
                }

                validatedMigrations.put(newKey, dataSnapshot);
                oldKeysToRemove.add(key);
            }
        }

        if (validatedMigrations.isEmpty()) {
            return;
        }

        // Phase 2: Apply migrations (copy data to new keys, keeping old keys as backup)
        for (Map.Entry<String, Map<String, Object>> entry : validatedMigrations.entrySet()) {
            String compositeKey = entry.getKey();
            Map<String, Object> dataSnapshot = entry.getValue();

            for (Map.Entry<String, Object> dataEntry : dataSnapshot.entrySet()) {
                config.set("signs." + compositeKey + "." + dataEntry.getKey(), dataEntry.getValue());
            }
        }

        // Phase 3: Save config with new data (old keys still exist as backup)
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save signs.yml during migration! Old data preserved.", e);
            // Abort migration - old keys remain intact as backup
            return;
        }

        // Phase 4: Only remove old keys after successful save
        for (String oldKey : oldKeysToRemove) {
            config.set("signs." + oldKey, null);
        }

        // Final save to remove old keys
        try {
            config.save(configFile);
            plugin.getLogger().info("Migrated " + validatedMigrations.size() + " sign(s) to world-aware format");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to clean up old sign keys. Data is safe but duplicated.", e);
        }
    }
    
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save signs.yml!", e);
        }
    }
    
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public void addSign(String region, Location location) {
        String compositeKey = location.getWorld().getName() + ":" + region;
        String path = "signs." + compositeKey;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getBlockX());
        config.set(path + ".y", location.getBlockY());
        config.set(path + ".z", location.getBlockZ());
        save();
    }

    public void addSupportBlock(String region, org.bukkit.World world, Location supportLoc, String blockType, String blockData) {
        String compositeKey = world.getName() + ":" + region;
        String path = "signs." + compositeKey + ".support-block";
        config.set(path + ".x", supportLoc.getBlockX());
        config.set(path + ".y", supportLoc.getBlockY());
        config.set(path + ".z", supportLoc.getBlockZ());
        config.set(path + ".original-type", blockType);
        config.set(path + ".original-data", blockData);
        save();
    }

    public boolean hasSupportBlock(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        return config.contains("signs." + compositeKey + ".support-block");
    }

    public Location getSupportBlockLocation(String region, org.bukkit.World world) {
        if (!hasSupportBlock(region, world)) {
            return null;
        }

        String compositeKey = world.getName() + ":" + region;
        String path = "signs." + compositeKey + ".support-block";
        Location signLoc = getSignLocation(region, world);
        if (signLoc == null) {
            return null;
        }

        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");

        return new Location(signLoc.getWorld(), x, y, z);
    }

    public Map<String, String> getSupportBlockData(String region, org.bukkit.World world) {
        if (!hasSupportBlock(region, world)) {
            return null;
        }

        String compositeKey = world.getName() + ":" + region;
        String path = "signs." + compositeKey + ".support-block";
        Map<String, String> data = new HashMap<>();
        data.put("type", config.getString(path + ".original-type", "STONE"));
        data.put("data", config.getString(path + ".original-data", ""));

        return data;
    }

    public void removeSupportBlock(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        config.set("signs." + compositeKey + ".support-block", null);
        save();
    }

    public void removeSign(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        config.set("signs." + compositeKey, null);
        save();
    }

    public boolean hasSign(String region, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + region;
        return config.contains("signs." + compositeKey);
    }

    public Location getSignLocation(String region, org.bukkit.World world) {
        if (!hasSign(region, world)) {
            return null;
        }

        String compositeKey = world.getName() + ":" + region;
        String path = "signs." + compositeKey;
        String worldName = config.getString(path + ".world");
        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");

        return new Location(plugin.getServer().getWorld(worldName), x, y, z);
    }
    
    public Map<String, Location> getAllSigns() {
        Map<String, Location> signs = new HashMap<>();

        if (!config.contains("signs")) {
            return signs;
        }

        for (String compositeKey : config.getConfigurationSection("signs").getKeys(false)) {
            // compositeKey is in format "world:region"
            String path = "signs." + compositeKey;
            String worldName = config.getString(path + ".world");
            int x = config.getInt(path + ".x");
            int y = config.getInt(path + ".y");
            int z = config.getInt(path + ".z");

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                Location loc = new Location(world, x, y, z);
                signs.put(compositeKey, loc);
            }
        }

        return signs;
    }
    
    /**
     * Get composite key (world:region) by sign location
     * @param location The location to search for
     * @return Composite key "world:region" or null if not found
     */
    public String getRegionByLocation(Location location) {
        for (Map.Entry<String, Location> entry : getAllSigns().entrySet()) {
            Location signLoc = entry.getValue();
            if (signLoc.getBlockX() == location.getBlockX() &&
                signLoc.getBlockY() == location.getBlockY() &&
                signLoc.getBlockZ() == location.getBlockZ() &&
                signLoc.getWorld().getName().equals(location.getWorld().getName())) {
                // Return composite key (already in world:region format)
                return entry.getKey();
            }
        }
        return null;
    }
}
