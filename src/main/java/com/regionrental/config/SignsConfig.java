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
        String path = "signs." + region;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getBlockX());
        config.set(path + ".y", location.getBlockY());
        config.set(path + ".z", location.getBlockZ());
        save();
    }

    public void addSupportBlock(String region, Location supportLoc, String blockType, String blockData) {
        String path = "signs." + region + ".support-block";
        config.set(path + ".x", supportLoc.getBlockX());
        config.set(path + ".y", supportLoc.getBlockY());
        config.set(path + ".z", supportLoc.getBlockZ());
        config.set(path + ".original-type", blockType);
        config.set(path + ".original-data", blockData);
        save();
    }

    public boolean hasSupportBlock(String region) {
        return config.contains("signs." + region + ".support-block");
    }

    public Location getSupportBlockLocation(String region) {
        if (!hasSupportBlock(region)) {
            return null;
        }

        String path = "signs." + region + ".support-block";
        Location signLoc = getSignLocation(region);
        if (signLoc == null) {
            return null;
        }

        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");

        return new Location(signLoc.getWorld(), x, y, z);
    }

    public Map<String, String> getSupportBlockData(String region) {
        if (!hasSupportBlock(region)) {
            return null;
        }

        String path = "signs." + region + ".support-block";
        Map<String, String> data = new HashMap<>();
        data.put("type", config.getString(path + ".original-type", "STONE"));
        data.put("data", config.getString(path + ".original-data", ""));

        return data;
    }

    public void removeSupportBlock(String region) {
        config.set("signs." + region + ".support-block", null);
        save();
    }
    
    public void removeSign(String region) {
        config.set("signs." + region, null);
        save();
    }
    
    public boolean hasSign(String region) {
        return config.contains("signs." + region);
    }
    
    public Location getSignLocation(String region) {
        if (!hasSign(region)) {
            return null;
        }
        
        String path = "signs." + region;
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
    
    public String getRegionByLocation(Location location) {
        for (Map.Entry<String, Location> entry : getAllSigns().entrySet()) {
            Location signLoc = entry.getValue();
            if (signLoc.getBlockX() == location.getBlockX() &&
                signLoc.getBlockY() == location.getBlockY() &&
                signLoc.getBlockZ() == location.getBlockZ() &&
                signLoc.getWorld().getName().equals(location.getWorld().getName())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
