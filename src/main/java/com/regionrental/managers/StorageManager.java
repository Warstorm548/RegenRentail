package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StorageManager implements Listener {
    
    private final RegionRental plugin;
    private final List<Material> containerTypes;
    private final Map<UUID, StorageGUISession> activeGUISessions;
    private final int ITEMS_PER_PAGE = 45; // 45 items + 9 slots for navigation
    
    public StorageManager(RegionRental plugin) {
        this.plugin = plugin;
        this.containerTypes = new ArrayList<>();
        this.activeGUISessions = new HashMap<>();
        initializeContainerTypes();
        
        // Register as listener for GUI events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void initializeContainerTypes() {
        // Add all container types
        containerTypes.add(Material.CHEST);
        containerTypes.add(Material.TRAPPED_CHEST);
        containerTypes.add(Material.BARREL);
        containerTypes.add(Material.HOPPER);
        containerTypes.add(Material.DROPPER);
        containerTypes.add(Material.DISPENSER);
        containerTypes.add(Material.FURNACE);
        containerTypes.add(Material.BLAST_FURNACE);
        containerTypes.add(Material.SMOKER);
        containerTypes.add(Material.BREWING_STAND);
        containerTypes.add(Material.SHULKER_BOX);
        containerTypes.add(Material.WHITE_SHULKER_BOX);
        containerTypes.add(Material.ORANGE_SHULKER_BOX);
        containerTypes.add(Material.MAGENTA_SHULKER_BOX);
        containerTypes.add(Material.LIGHT_BLUE_SHULKER_BOX);
        containerTypes.add(Material.YELLOW_SHULKER_BOX);
        containerTypes.add(Material.LIME_SHULKER_BOX);
        containerTypes.add(Material.PINK_SHULKER_BOX);
        containerTypes.add(Material.GRAY_SHULKER_BOX);
        containerTypes.add(Material.LIGHT_GRAY_SHULKER_BOX);
        containerTypes.add(Material.CYAN_SHULKER_BOX);
        containerTypes.add(Material.PURPLE_SHULKER_BOX);
        containerTypes.add(Material.BLUE_SHULKER_BOX);
        containerTypes.add(Material.BROWN_SHULKER_BOX);
        containerTypes.add(Material.GREEN_SHULKER_BOX);
        containerTypes.add(Material.RED_SHULKER_BOX);
        containerTypes.add(Material.BLACK_SHULKER_BOX);
    }
    
    public void storeItemsFromRegion(String regionName, UUID playerUUID) {
        World world = findWorldForRegion(regionName);
        if (world == null) {
            plugin.getLogger().warning("Could not find world for region " + regionName);
            return;
        }
        
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        
        if (regionManager == null) {
            return;
        }
        
        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            return;
        }
        
        List<ItemStack> allItems = new ArrayList<>();
        
        // Get region bounds
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        
        // Scan for containers in the region
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = loc.getBlock();
                    
                    if (containerTypes.contains(block.getType())) {
                        if (block.getState() instanceof Container) {
                            Container container = (Container) block.getState();
                            Inventory inv = container.getInventory();
                            
                            for (ItemStack item : inv.getContents()) {
                                if (item != null && item.getType() != Material.AIR) {
                                    allItems.add(item.clone());
                                }
                            }
                            
                            // Clear the container
                            inv.clear();
                            container.update();
                        }
                    }
                }
            }
        }
        
        // Store items if any were found
        if (!allItems.isEmpty()) {
            plugin.getStorageConfig().storeItems(playerUUID, regionName, allItems);
            
            // Notify player if online
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.getConfigManager().getMessage("items-stored", 
                    "{region}", regionName,
                    "{count}", String.valueOf(allItems.size())));
                
                // Notify about multi-page if needed
                if (allItems.size() > ITEMS_PER_PAGE) {
                    int pages = (allItems.size() - 1) / ITEMS_PER_PAGE + 1;
                    player.sendMessage(ChatColor.YELLOW + "Your items are stored across " + pages + " pages. Use /rrretrieve to access them.");
                }
            }
            
            plugin.getLogger().info("Stored " + allItems.size() + " items from region " + regionName);
        }
    }
    
    public void openRetrievalGUI(Player player) {
        List<ItemStack> items = plugin.getStorageConfig().getAllStoredItems(player.getUniqueId());
        
        if (items.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-stored-items"));
            return;
        }
        
        // Create GUI session
        StorageGUISession session = new StorageGUISession(player.getUniqueId(), items);
        activeGUISessions.put(player.getUniqueId(), session);
        
        // Open first page
        openGUIPage(player, session, 0);
    }
    
    private void openGUIPage(Player player, StorageGUISession session, int page) {
        int totalPages = session.getTotalPages();
        
        // Validate page number
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        
        session.currentPage = page;
        
        // Create inventory for this page
        String title = totalPages > 1 
            ? "Retrieved Items - Page " + (page + 1) + "/" + totalPages
            : "Retrieve Your Items";
        
        Inventory gui = Bukkit.createInventory(new StorageGUIHolder(player.getUniqueId()), 54, title);
        
        // Add items for current page
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, session.items.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = session.items.get(i);
            if (item != null) {
                gui.addItem(item);
            }
        }
        
        // Add navigation buttons if multiple pages
        if (totalPages > 1) {
            // Previous page button
            if (page > 0) {
                ItemStack prevButton = createNavigationButton(
                    Material.ARROW,
                    ChatColor.GREEN + "« Previous Page",
                    ChatColor.GRAY + "Click to go to page " + page
                );
                gui.setItem(45, prevButton);
            }
            
            // Page info
            ItemStack pageInfo = createNavigationButton(
                Material.PAPER,
                ChatColor.YELLOW + "Page " + (page + 1) + " of " + totalPages,
                ChatColor.GRAY + "Total items: " + session.items.size()
            );
            gui.setItem(49, pageInfo);
            
            // Next page button
            if (page < totalPages - 1) {
                ItemStack nextButton = createNavigationButton(
                    Material.ARROW,
                    ChatColor.GREEN + "Next Page »",
                    ChatColor.GRAY + "Click to go to page " + (page + 2)
                );
                gui.setItem(53, nextButton);
            }
            
            // Close button
            ItemStack closeButton = createNavigationButton(
                Material.BARRIER,
                ChatColor.RED + "Close",
                ChatColor.GRAY + "Click to close and save items"
            );
            gui.setItem(50, closeButton);
        }
        
        player.openInventory(gui);
    }
    
    private ItemStack createNavigationButton(Material material, String name, String lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
        return button;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageGUIHolder)) {
            return;
        }
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        StorageGUISession session = activeGUISessions.get(player.getUniqueId());
        
        if (session == null) {
            return;
        }
        
        int slot = event.getSlot();
        
        // Handle navigation buttons
        if (slot >= 45 && slot <= 53) {
            event.setCancelled(true);
            
            if (slot == 45 && session.currentPage > 0) {
                // Previous page
                openGUIPage(player, session, session.currentPage - 1);
            } else if (slot == 53 && session.currentPage < session.getTotalPages() - 1) {
                // Next page
                openGUIPage(player, session, session.currentPage + 1);
            } else if (slot == 50) {
                // Close button
                player.closeInventory();
            }
        }
        // Allow taking items from other slots
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageGUIHolder)) {
            return;
        }
        
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Clean up session
        StorageGUISession session = activeGUISessions.remove(playerUUID);
        
        if (session != null && session.isFirstOpen) {
            // Clear storage after first retrieval
            plugin.getStorageConfig().clearPlayerStorage(playerUUID);
            player.sendMessage(plugin.getConfigManager().getMessage("items-retrieved"));
        }
    }
    
    private World findWorldForRegion(String regionName) {
        // Try to find the world that contains this region
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            
            if (regionManager != null && regionManager.hasRegion(regionName)) {
                return world;
            }
        }
        
        // Default to first world if not found
        return Bukkit.getWorlds().get(0);
    }
    
    // Inner class for GUI session tracking
    private static class StorageGUISession {
        final UUID playerUUID;
        final List<ItemStack> items;
        int currentPage;
        boolean isFirstOpen = true;
        
        StorageGUISession(UUID playerUUID, List<ItemStack> items) {
            this.playerUUID = playerUUID;
            this.items = items;
            this.currentPage = 0;
        }
        
        int getTotalPages() {
            return Math.max(1, (items.size() - 1) / 45 + 1);
        }
    }
    
    // Custom InventoryHolder for identification
    private static class StorageGUIHolder implements InventoryHolder {
        private final UUID playerUUID;
        
        StorageGUIHolder(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }
        
        @Override
        public Inventory getInventory() {
            return null;
        }
        
        public UUID getPlayerUUID() {
            return playerUUID;
        }
    }
}
