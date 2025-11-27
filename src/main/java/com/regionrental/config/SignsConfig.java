package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

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
     */
    private void migrateToWorldAwareKeys() {
        if (!config.contains("signs")) {
            return;
        }

        ConfigurationSection signsSection = config.getConfigurationSection("signs");
        if (signsSection == null) {
            return;
        }

        Map<String, ConfigurationSection> migrations = new HashMap<>();
        int migratedCount = 0;

        // Find old format entries (no ":" in key)
        for (String key : signsSection.getKeys(false)) {
            if (!key.contains(":")) {
                // Old format - has world field, use it
                String world = config.getString("signs." + key + ".world");
                if (world == null || world.isEmpty()) {
                    plugin.getLogger().warning("Sign '" + key + "' has no world field, skipping migration");
                    continue;
                }

                String newKey = world + ":" + key;

                // Copy all data to new key
                ConfigurationSection oldSection = config.getConfigurationSection("signs." + key);
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
                    config.set("signs." + compositeKey + "." + subKey, value);
                }
            }

            // Remove old keys (those without ":")
            for (String key : new HashSet<>(signsSection.getKeys(false))) {
                if (!key.contains(":")) {
                    config.set("signs." + key, null);
                }
            }

            save();
            plugin.getLogger().info("Migrated " + migratedCount + " sign(s) to world-aware format");
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
        
        for (String region : config.getConfigurationSection("signs").getKeys(false)) {
            Location loc = getSignLocation(region);
            if (loc != null) {
                signs.put(region, loc);
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
