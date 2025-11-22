package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class StorageConfig {
    
    private final RegionRental plugin;
    private File configFile;
    private FileConfiguration config;
    
    public StorageConfig(RegionRental plugin) {
        this.plugin = plugin;
        createConfig();
    }
    
    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "storage.yml");
        
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
                plugin.getLogger().info("Created storage.yml file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create storage.yml!", e);
            }
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save storage.yml!", e);
        }
    }
    
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    public void storeItems(UUID playerUUID, String region, List<ItemStack> items) {
        storeItems(playerUUID, region, items, new ArrayList<>());
    }

    public void storeItems(UUID playerUUID, String region, List<ItemStack> containerItems, List<ItemStack> blockItems) {
        String path = "storage." + playerUUID.toString() + "." + region;
        config.set(path + ".timestamp", System.currentTimeMillis());
        config.set(path + ".items", containerItems);
        config.set(path + ".blocks", blockItems);
        save();
    }
    
    public List<ItemStack> retrieveItems(UUID playerUUID, String region) {
        String path = "storage." + playerUUID.toString() + "." + region + ".items";
        
        if (!config.contains(path)) {
            return new ArrayList<>();
        }
        
        @SuppressWarnings("unchecked")
        List<ItemStack> items = (List<ItemStack>) config.getList(path, new ArrayList<>());
        
        // Remove the items after retrieval
        config.set("storage." + playerUUID.toString() + "." + region, null);
        
        // Clean up empty sections
        if (config.getConfigurationSection("storage." + playerUUID.toString()) != null &&
            config.getConfigurationSection("storage." + playerUUID.toString()).getKeys(false).isEmpty()) {
            config.set("storage." + playerUUID.toString(), null);
        }
        
        save();
        return items;
    }
    
    public List<ItemStack> getAllStoredItems(UUID playerUUID) {
        List<ItemStack> allItems = new ArrayList<>();
        String basePath = "storage." + playerUUID.toString();

        if (!config.contains(basePath)) {
            return allItems;
        }

        for (String region : config.getConfigurationSection(basePath).getKeys(false)) {
            // Get container items
            @SuppressWarnings("unchecked")
            List<ItemStack> items = (List<ItemStack>) config.getList(basePath + "." + region + ".items", new ArrayList<>());
            allItems.addAll(items);

            // Get block items
            @SuppressWarnings("unchecked")
            List<ItemStack> blocks = (List<ItemStack>) config.getList(basePath + "." + region + ".blocks", new ArrayList<>());
            allItems.addAll(blocks);
        }

        return allItems;
    }
    
    public void clearPlayerStorage(UUID playerUUID) {
        config.set("storage." + playerUUID.toString(), null);
        save();
    }

    /**
     * Updates storage with remaining items after partial retrieval
     * If no items remain, clears storage completely
     * @param playerUUID The player's UUID
     * @param remainingItems List of items that weren't taken from the GUI
     */
    public void updatePartialStorage(UUID playerUUID, List<ItemStack> remainingItems) {
        if (remainingItems.isEmpty()) {
            // No items left, clear completely
            clearPlayerStorage(playerUUID);
            return;
        }

        // Clear existing storage first
        clearPlayerStorage(playerUUID);

        // Re-store remaining items under a generic region name
        // Note: Original region information is lost, but players just want their items back
        storeItems(playerUUID, "partial_retrieval", remainingItems, new ArrayList<>());
    }

    public boolean hasStoredItems(UUID playerUUID) {
        return config.contains("storage." + playerUUID.toString());
    }
    
    public List<String> getStoredRegions(UUID playerUUID) {
        List<String> regions = new ArrayList<>();
        String basePath = "storage." + playerUUID.toString();
        
        if (!config.contains(basePath)) {
            return regions;
        }
        
        regions.addAll(config.getConfigurationSection(basePath).getKeys(false));
        return regions;
    }
    
    public long getStorageTimestamp(UUID playerUUID, String region) {
        String path = "storage." + playerUUID.toString() + "." + region + ".timestamp";
        return config.getLong(path, 0L);
    }
    
    public void cleanupOldStorage(long maxAge) {
        long currentTime = System.currentTimeMillis();
        
        if (!config.contains("storage")) {
            return;
        }
        
        for (String uuid : config.getConfigurationSection("storage").getKeys(false)) {
            for (String region : config.getConfigurationSection("storage." + uuid).getKeys(false)) {
                long timestamp = config.getLong("storage." + uuid + "." + region + ".timestamp", 0L);
                
                if (currentTime - timestamp > maxAge) {
                    config.set("storage." + uuid + "." + region, null);
                    plugin.getLogger().info("Cleaned up old storage for " + uuid + " in region " + region);
                }
            }
        }
        
        save();
    }
}
