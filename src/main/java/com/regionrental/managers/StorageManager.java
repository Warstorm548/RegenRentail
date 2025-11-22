package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
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
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StorageManager implements Listener {

    private final RegionRental plugin;
    private final List<Material> containerTypes;
    private final Map<UUID, StorageGUISession> activeGUISessions;
    private final int ITEMS_PER_PAGE = 45; // 45 items + 9 slots for navigation

    // Blocks that should NOT be stored (too common/cheap)
    private static final Set<Material> DEFAULT_BLOCK_BLACKLIST = new HashSet<>(Arrays.asList(
        Material.AIR,
        Material.CAVE_AIR,
        Material.VOID_AIR,
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.STONE,
        Material.COBBLESTONE,
        Material.GRAVEL,
        Material.SAND,
        Material.SANDSTONE,
        Material.WATER,
        Material.LAVA,
        Material.BEDROCK
    ));
    
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
    
    /**
     * Scans and collects items from containers in a region
     * Also clears the containers
     * @return List of ItemStacks found in containers
     */
    public List<ItemStack> collectItemsFromRegion(String regionName) {
        List<ItemStack> allItems = new ArrayList<>();

        World world = findWorldForRegion(regionName);
        if (world == null) {
            plugin.getLogger().warning("Could not find world for region " + regionName);
            return allItems;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            return allItems;
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            return allItems;
        }

        // Get region bounds
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Scan for containers in the region
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    Location loc = new Location(world, x, y, z);
                    Block block = loc.getBlock();

                    if (containerTypes.contains(block.getType())) {
                        // Skip shulker boxes - they preserve items when stored as blocks (vanilla behavior)
                        if (isShulkerBox(block.getType())) {
                            continue;
                        }

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

        return allItems;
    }

    /**
     * Legacy method - stores items from containers (backward compatibility)
     */
    public void storeItemsFromRegion(String regionName, UUID playerUUID) {
        List<ItemStack> allItems = collectItemsFromRegion(regionName);

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

    /**
     * Stores both container items and player-placed blocks together
     * This is the recommended method for rental expiration
     */
    public void storeItemsAndBlocksFromRegion(String regionName, UUID playerUUID) {
        // Collect container items
        List<ItemStack> containerItems = collectItemsFromRegion(regionName);

        // Collect player-placed blocks
        List<ItemStack> playerBlocks = storePlayerBlocksFromRegion(regionName, playerUUID);

        // Store both together
        if (!containerItems.isEmpty() || !playerBlocks.isEmpty()) {
            plugin.getStorageConfig().storeItems(playerUUID, regionName, containerItems, playerBlocks);

            int totalItems = containerItems.size() + playerBlocks.size();

            // Notify player if online
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.GREEN + "Stored " + containerItems.size() +
                    " items and " + playerBlocks.size() + " blocks from " + regionName);

                // Notify about multi-page if needed
                if (totalItems > ITEMS_PER_PAGE) {
                    int pages = (totalItems - 1) / ITEMS_PER_PAGE + 1;
                    player.sendMessage(ChatColor.YELLOW + "Your items are stored across " + pages + " pages. Use /rrretrieve to access them.");
                }
            }

            plugin.getLogger().info("Stored " + containerItems.size() + " items and " +
                playerBlocks.size() + " blocks from region " + regionName);
        }
    }

    /**
     * Stores player-placed blocks from a region by comparing current state to original schematic
     * @param regionName The WorldGuard region name
     * @param playerUUID The player's UUID
     * @return List of ItemStacks representing player-placed blocks
     */
    public List<ItemStack> storePlayerBlocksFromRegion(String regionName, UUID playerUUID) {
        List<ItemStack> playerBlocks = new ArrayList<>();

        // Check if block storage is enabled
        if (!plugin.getConfigManager().isBlockStorage()) {
            return playerBlocks;
        }

        // Get the original schematic
        Clipboard clipboard = plugin.getWorldEditManager().getClipboard(regionName);
        if (clipboard == null) {
            plugin.getLogger().warning("No schematic found for region " + regionName + " - cannot compare blocks");
            return playerBlocks;
        }

        World world = findWorldForRegion(regionName);
        if (world == null) {
            plugin.getLogger().warning("Could not find world for region " + regionName);
            return playerBlocks;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            return playerBlocks;
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region == null) {
            return playerBlocks;
        }

        // Get region bounds
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        BlockVector3 origin = clipboard.getOrigin();

        // Build blacklist from config
        Set<Material> blockBlacklist = buildBlockBlacklist();

        int blocksCompared = 0;
        int blocksStored = 0;

        // Scan all blocks in the region
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    blocksCompared++;

                    Location loc = new Location(world, x, y, z);
                    Block currentBlock = loc.getBlock();
                    Material currentMaterial = currentBlock.getType();

                    // Skip blacklisted blocks
                    if (blockBlacklist.contains(currentMaterial)) {
                        continue;
                    }

                    // Get the corresponding block from the schematic
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    BlockState originalBlockState = clipboard.getBlock(pos);

                    // Convert WorldEdit BlockState to Bukkit Material
                    String originalBlockType = originalBlockState.getBlockType().getId();
                    Material originalMaterial = Material.matchMaterial(originalBlockType.replace("minecraft:", "").toUpperCase());

                    // Compare blocks - store if different
                    if (originalMaterial == null || currentMaterial != originalMaterial) {
                        // Block was placed by player (different from schematic)
                        ItemStack blockItem = blockToItemStack(currentBlock, regionName, x, y, z);
                        if (blockItem != null) {
                            playerBlocks.add(blockItem);
                            blocksStored++;
                        }
                    }
                }
            }
        }

        // Log results
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Block comparison for " + regionName + ": " +
                    blocksCompared + " blocks scanned, " + blocksStored + " player-placed blocks stored");
        }

        // Notify player if blocks were stored
        if (!playerBlocks.isEmpty()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.GREEN + "Stored " + blocksStored +
                    " player-placed blocks from " + regionName);
            }
        }

        return playerBlocks;
    }

    /**
     * Preserves container state (NBT data, inventory) on an ItemStack
     * For shulker boxes: preserves full inventory contents
     * For other containers: preserves custom names and other metadata
     */
    private void preserveContainerState(ItemStack item, Block block) {
        if (!(block.getState() instanceof Container)) {
            return;
        }

        Container container = (Container) block.getState();
        ItemMeta itemMeta = item.getItemMeta();

        if (itemMeta == null) {
            return;
        }

        // Preserve container BlockState (includes inventory for shulker boxes)
        if (itemMeta instanceof BlockStateMeta) {
            BlockStateMeta blockStateMeta = (BlockStateMeta) itemMeta;
            blockStateMeta.setBlockState(container);
        }

        // Preserve custom name if present
        if (container.getCustomName() != null) {
            itemMeta.setDisplayName(container.getCustomName());
        }

        item.setItemMeta(itemMeta);
    }

    /**
     * Converts a block to an ItemStack with metadata
     */
    private ItemStack blockToItemStack(Block block, String regionName, int x, int y, int z) {
        Material type = block.getType();

        // Skip air and invalid materials
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return null;
        }

        ItemStack item = new ItemStack(type, 1);

        // Preserve container state for containers (NBT data, inventory for shulker boxes)
        if (containerTypes.contains(type)) {
            preserveContainerState(item, block);
        }

        // Add metadata showing it's a player-placed block
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.BLUE + "Player-Placed Block");
            lore.add(ChatColor.GRAY + "From: " + regionName);
            lore.add(ChatColor.GRAY + "Location: " + x + ", " + y + ", " + z);

            // For shulker boxes, add note about preserved contents
            if (isShulkerBox(type)) {
                lore.add(ChatColor.GREEN + "Contents Preserved");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Builds the block blacklist from config
     */
    private Set<Material> buildBlockBlacklist() {
        Set<Material> blacklist = new HashSet<>(DEFAULT_BLOCK_BLACKLIST);

        // Add blocks from config
        List<String> configBlacklist = plugin.getConfigManager().getBlockBlacklist();
        for (String materialName : configBlacklist) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                blacklist.add(material);
            } else {
                plugin.getLogger().warning("Invalid material in block blacklist: " + materialName);
            }
        }

        return blacklist;
    }

    /**
     * Checks if a material is a shulker box (any color)
     */
    private boolean isShulkerBox(Material material) {
        return material.name().endsWith("SHULKER_BOX");
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
