package com.regionrental.managers;

import com.regionrental.RegionRental;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RentalManager {

    private final RegionRental plugin;
    private final Map<String, Rental> rentals;
    private File rentalsFile;
    private FileConfiguration rentalsConfig;

    // Dirty tracking for optimized saves
    private volatile boolean isDirty = false;
    private final Set<String> modifiedRentals = ConcurrentHashMap.newKeySet();

    // Secondary indexes for O(1) player/member lookups
    // playerUUID -> Set of composite keys (world:region) they OWN
    private final Map<UUID, Set<String>> playerRentalIndex = new ConcurrentHashMap<>();
    // playerUUID -> Set of composite keys (world:region) they are a MEMBER of (not owner)
    private final Map<UUID, Set<String>> memberRentalIndex = new ConcurrentHashMap<>();
    
    public RentalManager(RegionRental plugin) {
        this.plugin = plugin;
        this.rentals = new ConcurrentHashMap<>();
        setupRentalsFile();
    }
    
    private void setupRentalsFile() {
        rentalsFile = new File(plugin.getDataFolder(), "rentals.yml");
        
        if (!rentalsFile.exists()) {
            rentalsFile.getParentFile().mkdirs();
            try {
                rentalsFile.createNewFile();
                plugin.getLogger().info("Created rentals.yml file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create rentals.yml!", e);
            }
        }
        
        rentalsConfig = YamlConfiguration.loadConfiguration(rentalsFile);
    }
    
    public void loadAllRentals() {
        rentals.clear();
        playerRentalIndex.clear();
        memberRentalIndex.clear();

        if (!rentalsConfig.contains("rentals")) {
            return;
        }

        int migratedCount = 0;
        String defaultWorld = plugin.getServer().getWorlds().get(0).getName();

        for (String configKey : rentalsConfig.getConfigurationSection("rentals").getKeys(false)) {
            String path = "rentals." + configKey;

            try {
                UUID playerUUID = UUID.fromString(rentalsConfig.getString(path + ".player-uuid"));
                String playerName = rentalsConfig.getString(path + ".player-name");
                long startDate = rentalsConfig.getLong(path + ".start-date");
                long endDate = rentalsConfig.getLong(path + ".end-date");
                int extensionCount = rentalsConfig.getInt(path + ".extension-count", 0);
                double totalPaid = rentalsConfig.getDouble(path + ".total-paid", 0);
                double initialPrice = rentalsConfig.getDouble(path + ".initial-price", totalPaid);

                // Load world field (with migration for old data)
                // NEW format: composite key (world:region) with region-name field
                // OLD format: just region name as key
                String worldName = rentalsConfig.getString(path + ".world", null);
                String regionName = rentalsConfig.getString(path + ".region-name", null);

                if (regionName == null) {
                    // OLD format: configKey IS the region name
                    regionName = configKey;
                }

                if (worldName == null) {
                    // Migration: Old rental without world field - default to first world
                    worldName = defaultWorld;
                    migratedCount++;
                }

                // Load refund tracking data (backwards compatible)
                double totalRefunded = rentalsConfig.getDouble(path + ".total-refunded", 0.0);
                List<Rental.RefundRecord> refundHistory = new ArrayList<>();

                if (rentalsConfig.contains(path + ".refund-history")) {
                    List<Map<?, ?>> refundList = rentalsConfig.getMapList(path + ".refund-history");
                    for (Map<?, ?> refundData : refundList) {
                        double amount = ((Number) refundData.get("amount")).doubleValue();
                        long timestamp = ((Number) refundData.get("timestamp")).longValue();
                        String reason = (String) refundData.get("reason");
                        String adminName = (String) refundData.get("admin");

                        refundHistory.add(new Rental.RefundRecord(amount, timestamp, reason, adminName));
                    }
                }

                // Load members (backwards compatible)
                Set<UUID> members = new HashSet<>();
                if (rentalsConfig.contains(path + ".members")) {
                    List<String> memberList = rentalsConfig.getStringList(path + ".members");
                    for (String uuidStr : memberList) {
                        try {
                            members.add(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid member UUID in rental " + configKey + ": " + uuidStr);
                        }
                    }
                }

                Rental rental = Rental.fromStorage(regionName, worldName, playerUUID, playerName, startDate, endDate,
                        extensionCount, totalPaid, initialPrice, totalRefunded, refundHistory, members);

                // Use composite key (world:region) for storage
                String compositeKey = rental.getCompositeKey();
                rentals.put(compositeKey, rental);

                // Update secondary indexes
                addToPlayerIndex(playerUUID, compositeKey);
                for (UUID memberUUID : members) {
                    addToMemberIndex(memberUUID, compositeKey);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load rental for config key " + configKey + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + rentals.size() + " rentals");

        if (migratedCount > 0) {
            plugin.getLogger().info("Migrated " + migratedCount + " rental(s) to multi-world format (defaulted to '" + defaultWorld + "')");
            saveAllRentals(); // Save migrated data
        }
    }
    
    /**
     * Marks a rental as modified for dirty tracking
     * @param compositeKey The composite key (world:region) of the modified rental
     */
    private void markDirty(String compositeKey) {
        isDirty = true;
        if (compositeKey != null) {
            modifiedRentals.add(compositeKey);
        }
    }

    // ========== Secondary Index Management ==========

    /**
     * Adds a rental to the player ownership index
     */
    private void addToPlayerIndex(UUID playerUUID, String compositeKey) {
        playerRentalIndex.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet()).add(compositeKey);
    }

    /**
     * Removes a rental from the player ownership index
     */
    private void removeFromPlayerIndex(UUID playerUUID, String compositeKey) {
        Set<String> rentalsSet = playerRentalIndex.get(playerUUID);
        if (rentalsSet != null) {
            rentalsSet.remove(compositeKey);
            if (rentalsSet.isEmpty()) {
                playerRentalIndex.remove(playerUUID);
            }
        }
    }

    /**
     * Adds a rental to the member index
     */
    private void addToMemberIndex(UUID memberUUID, String compositeKey) {
        memberRentalIndex.computeIfAbsent(memberUUID, k -> ConcurrentHashMap.newKeySet()).add(compositeKey);
    }

    /**
     * Removes a rental from the member index
     */
    private void removeFromMemberIndex(UUID memberUUID, String compositeKey) {
        Set<String> rentalsSet = memberRentalIndex.get(memberUUID);
        if (rentalsSet != null) {
            rentalsSet.remove(compositeKey);
            if (rentalsSet.isEmpty()) {
                memberRentalIndex.remove(memberUUID);
            }
        }
    }

    /**
     * Removes a rental from all member indexes (used when rental expires)
     */
    private void removeAllMembersFromIndex(Rental rental) {
        String compositeKey = rental.getCompositeKey();
        for (UUID memberUUID : rental.getMembers()) {
            removeFromMemberIndex(memberUUID, compositeKey);
        }
    }

    /**
     * Checks if there are unsaved changes
     * @return true if there are changes that need to be saved
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Saves rentals only if there are unsaved changes
     * Called by the auto-save task to avoid unnecessary I/O
     */
    public void saveIfDirty() {
        if (isDirty) {
            saveAllRentals();
        }
    }

    public void saveAllRentals() {
        // Skip save if nothing has changed
        if (!isDirty && modifiedRentals.isEmpty()) {
            return;
        }

        // Clear existing data
        rentalsConfig = new YamlConfiguration();

        for (Map.Entry<String, Rental> entry : rentals.entrySet()) {
            Rental rental = entry.getValue();
            // Use composite key (world:region) for unique identification
            String path = "rentals." + rental.getCompositeKey();

            rentalsConfig.set(path + ".region-name", rental.getRegionName());
            rentalsConfig.set(path + ".world", rental.getWorldName());
            rentalsConfig.set(path + ".player-uuid", rental.getPlayerUUID().toString());
            rentalsConfig.set(path + ".player-name", rental.getPlayerName());
            rentalsConfig.set(path + ".start-date", rental.getStartDate());
            rentalsConfig.set(path + ".end-date", rental.getEndDate());
            rentalsConfig.set(path + ".extension-count", rental.getExtensionCount());
            rentalsConfig.set(path + ".total-paid", rental.getTotalPaid());
            rentalsConfig.set(path + ".initial-price", rental.getInitialPrice());
            rentalsConfig.set(path + ".total-refunded", rental.getTotalRefunded());

            // Save refund history
            List<Map<String, Object>> refundHistoryData = new ArrayList<>();
            for (Rental.RefundRecord record : rental.getRefundHistory()) {
                Map<String, Object> refundData = new HashMap<>();
                refundData.put("amount", record.getAmount());
                refundData.put("timestamp", record.getTimestamp());
                refundData.put("reason", record.getReason());
                refundData.put("admin", record.getAdminName());
                refundHistoryData.add(refundData);
            }
            rentalsConfig.set(path + ".refund-history", refundHistoryData);

            // Save members
            List<String> memberUUIDs = new ArrayList<>();
            for (UUID memberUUID : rental.getMembers()) {
                memberUUIDs.add(memberUUID.toString());
            }
            rentalsConfig.set(path + ".members", memberUUIDs);
        }

        try {
            rentalsConfig.save(rentalsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save rentals.yml!", e);
            return;
        }
        // Reset dirty flags after successful save
        isDirty = false;
        modifiedRentals.clear();
    }
    
    public boolean createRental(String regionName, org.bukkit.World world, Player player, int days, double price) {
        String compositeKey = world.getName() + ":" + regionName;

        // Check if region is already rented
        if (isRented(regionName, world)) {
            return false;
        }

        // Check player rental limit
        if (getPlayerRentals(player.getUniqueId()).size() >= plugin.getConfigManager().getMaxRentalsPerPlayer()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-rentals-reached"));
            return false;
        }

        // Capture region state with WorldEdit before renting (if enabled)
        if (plugin.getConfigManager().isBlockRestoration()) {
            boolean captured = plugin.getWorldEditManager().captureRegion(regionName, world);
            if (!captured && plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Failed to capture region state for: " + regionName + " in world " + world.getName());
            }
        }

        // Create the rental
        long endDate = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L);
        Rental rental = Rental.create(regionName, world.getName(), player.getUniqueId(), player.getName(), endDate, price);

        rentals.put(compositeKey, rental);

        // Update player ownership index
        addToPlayerIndex(player.getUniqueId(), compositeKey);

        // Add player to region
        plugin.getWorldGuardManager().addPlayerToRegion(regionName, world, player.getUniqueId());

        // Mark sign as dirty for next update cycle
        plugin.getSignManager().markSignDirty(regionName, world);

        // Mark for save (dirty tracking)
        markDirty(compositeKey);

        return true;
    }

    public boolean extendRental(String regionName, org.bukkit.World world, Player player, int days, double price) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);

        if (rental == null || !rental.getPlayerUUID().equals(player.getUniqueId())) {
            return false;
        }

        // Check extension limit
        if (rental.getExtensionCount() >= plugin.getConfigManager().getMaxExtensions()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-extensions-reached"));
            return false;
        }

        // Extend the rental
        rental.extendRental(days, price);

        // Mark sign as dirty for next update cycle
        plugin.getSignManager().markSignDirty(regionName, world);

        // Mark for save (dirty tracking)
        markDirty(compositeKey);

        return true;
    }

    public void expireRental(String regionName, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);

        if (rental == null) {
            return;
        }

        // Remove player from region
        plugin.getWorldGuardManager().removePlayerFromRegion(regionName, world, rental.getPlayerUUID());

        // Remove all members from region and member index
        removeAllMembersFromIndex(rental);
        for (UUID memberUUID : rental.getMembers()) {
            plugin.getWorldGuardManager().removePlayerFromRegion(regionName, world, memberUUID);
        }
        rental.clearMembers();

        // Store container items and player-placed blocks (must happen BEFORE restoration)
        if (plugin.getConfigManager().isItemStorage()) {
            plugin.getStorageManager().storeItemsAndBlocksFromRegion(regionName, world, rental.getPlayerUUID());
        }

        // Remove EzChestShop shops from region (AFTER storage scan, BEFORE restoration)
        if (plugin.getEzChestShopManager() != null && plugin.getEzChestShopManager().isEnabled()) {
            int shopsRemoved = plugin.getEzChestShopManager().removeShopsInRegion(regionName, world);

            // Notify player if configured and shops were removed
            if (shopsRemoved > 0 && plugin.getConfigManager().isEzChestShopNotifyOnRemoval()) {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(rental.getPlayerUUID());
                if (player != null && player.isOnline()) {
                    String message = plugin.getConfigManager().getEzChestShopRemovalMessage();
                    message = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().getPrefix() + message.replace("{region}", regionName));
                    player.sendMessage(message);
                }
            }
        }

        // Restore region blocks with WorldEdit (if enabled)
        if (plugin.getConfigManager().isBlockRestoration()) {
            boolean restored = plugin.getWorldEditManager().restoreRegion(regionName, world);
            if (restored) {
                plugin.getLogger().info("Restored blocks for region: " + regionName + " in world " + world.getName());
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("Failed to restore blocks for region: " + regionName + " in world " + world.getName());
            }
        }

        // Remove from player ownership index
        removeFromPlayerIndex(rental.getPlayerUUID(), compositeKey);

        // Remove rental
        rentals.remove(compositeKey);

        // Mark sign as dirty for next update cycle
        plugin.getSignManager().markSignDirty(regionName, world);

        // Mark for save (dirty tracking) - use null since rental was removed
        markDirty(null);

        plugin.getLogger().info("Rental expired for region " + regionName + " in world " + world.getName());
    }


    /**
     * Issues a refund to a player with safety checks and audit trail
     * @param rental The rental to refund
     * @param amount The amount to refund
     * @param reason The reason for the refund (e.g., "duration_reset", "time_removal", "admin_reset")
     * @param adminName The admin issuing the refund
     * @return Map with refund details (success, actualAmount, message) or null if refund failed
     */
    public Map<String, Object> issueRefund(Rental rental, double amount, String reason, String adminName) {
        if (rental == null || amount <= 0) {
            return null;
        }

        // Safety check: Never refund more than net refundable amount
        double maxRefund = rental.getNetRefundableAmount();
        double actualRefund = Math.min(amount, maxRefund);

        if (actualRefund <= 0) {
            // Nothing to refund
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("actualAmount", 0.0);
            result.put("message", "No refundable amount remaining");
            return result;
        }

        // Issue refund via Vault
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(rental.getPlayerUUID()), actualRefund);

            // Record refund in rental history
            rental.recordRefund(actualRefund, reason, adminName);

            // Mark for save (dirty tracking)
            markDirty(rental.getCompositeKey());

            // Log to console
            String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), actualRefund);
            plugin.getLogger().info("Refund issued to " + rental.getPlayerName() + " for " + rental.getRegionName() + ": " +
                    formattedAmount + " (Reason: " + reason + " by " + adminName + ")");

            // Notify player if online
            Player player = plugin.getServer().getPlayer(rental.getPlayerUUID());
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.getConfigManager().getMessage("refund-issued",
                        "{player}", rental.getPlayerName(),
                        "{amount}", formattedAmount,
                        "{reason}", reason));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("actualAmount", actualRefund);
            result.put("message", "Refund issued successfully");
            return result;
        }

        return null;
    }

    /**
     * Calculates proportional refund based on time removed
     * @param rental The rental to calculate refund for
     * @param daysRemoved Number of days being removed
     * @return The proportional refund amount
     */
    public double calculateProportionalRefund(Rental rental, int daysRemoved) {
        if (rental == null || daysRemoved <= 0) {
            return 0.0;
        }

        // Get total rental duration in milliseconds
        long totalDuration = rental.getEndDate() - rental.getStartDate();
        long daysInMs = daysRemoved * 24L * 60L * 60L * 1000L;

        // Calculate proportion of time being removed
        double proportion = Math.min(1.0, (double) daysInMs / (double) totalDuration);

        // Calculate refund from net paid amount (paid - already refunded)
        double netPaid = rental.getTotalPaid() - rental.getTotalRefunded();
        return Math.max(0, netPaid * proportion);
    }

    /**
     * Charges a player for admin-added time
     * @param rental The rental to add time to
     * @param days Number of days to add
     * @param player The player to charge
     * @return true if successful, false otherwise
     */
    public boolean chargeDurationAdd(Rental rental, int days, Player player) {
        if (rental == null || days <= 0 || player == null) {
            return false;
        }

        // Get extension price per day from config
        org.bukkit.World world = plugin.getServer().getWorld(rental.getWorldName());
        double extensionPrice = plugin.getConfigManager().getExtensionPrice(rental.getRegionName(), world);
        double totalCost = extensionPrice * days;

        // Check if player has enough money
        if (plugin.getEconomy() != null) {
            if (plugin.getEconomy().getBalance(player) < totalCost) {
                return false;
            }

            // Withdraw money
            plugin.getEconomy().withdrawPlayer(player, totalCost);

            // Add time with charge (does NOT increment extension count)
            rental.addTimeWithCharge(days, totalCost);

            // Mark for save (dirty tracking)
            markDirty(rental.getCompositeKey());

            // Mark sign as dirty for next update cycle
            if (world != null) {
                plugin.getSignManager().markSignDirty(rental.getRegionName(), world);
            }

            // Log
            String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), totalCost);
            plugin.getLogger().info("Player " + player.getName() + " charged " + formattedAmount +
                    " for " + days + " days added to " + rental.getRegionName());

            return true;
        }

        return false;
    }

    /**
     * Resets a rental with net refund (prevents double-refunds) for admin use
     * @param regionName The region to reset
     * @param world The world the region is in
     * @return A map containing refund details (playerUUID, playerName, refundAmount, alreadyRefunded) or null if rental doesn't exist
     */
    public Map<String, Object> resetRentalWithRefund(String regionName, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);

        if (rental == null) {
            return null;
        }

        // Get rental information before expiring
        UUID playerUUID = rental.getPlayerUUID();
        String playerName = rental.getPlayerName();
        double totalPaid = rental.getTotalPaid();
        double alreadyRefunded = rental.getTotalRefunded();
        double netRefund = rental.getNetRefundableAmount();

        // Issue net refund using the centralized refund system
        if (netRefund > 0) {
            Map<String, Object> refundResult = issueRefund(rental, netRefund, "admin_reset", "Admin");

            if (refundResult != null && (boolean) refundResult.get("success")) {
                double actualRefund = (double) refundResult.get("actualAmount");

                // Notify player if online (additional notification for admin reset)
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), actualRefund);
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-reset-refund",
                            "{region}", regionName,
                            "{amount}", formattedAmount));
                }
            }
        }

        // Now expire the rental (this removes player from region, stores items, restores blocks, etc.)
        expireRental(regionName, world);

        // Return details for admin notification
        Map<String, Object> refundDetails = new HashMap<>();
        refundDetails.put("playerUUID", playerUUID);
        refundDetails.put("playerName", playerName);
        refundDetails.put("refundAmount", netRefund);
        refundDetails.put("totalPaid", totalPaid);
        refundDetails.put("alreadyRefunded", alreadyRefunded);

        return refundDetails;
    }



    public void resetRentalTime(String regionName, org.bukkit.World world, int days) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);
        if (rental != null) {
            rental.resetTime(days);
            plugin.getSignManager().updateSign(regionName, world);
            markDirty(compositeKey);
        }
    }

    public boolean isRented(String regionName, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + regionName;
        return rentals.containsKey(compositeKey);
    }

    public Rental getRental(String regionName, org.bukkit.World world) {
        String compositeKey = world.getName() + ":" + regionName;
        return rentals.get(compositeKey);
    }
    /**
     * Gets all rentals owned by a player using O(1) index lookup
     * @param playerUUID The player's UUID
     * @return List of rentals owned by the player
     */
    public List<Rental> getPlayerRentals(UUID playerUUID) {
        Set<String> playerKeys = playerRentalIndex.get(playerUUID);
        if (playerKeys == null || playerKeys.isEmpty()) {
            return new ArrayList<>();
        }

        List<Rental> result = new ArrayList<>(playerKeys.size());
        for (String compositeKey : playerKeys) {
            Rental rental = rentals.get(compositeKey);
            if (rental != null) {
                result.add(rental);
            }
        }
        return result;
    }

    /**
     * Gets all rentals where player is a member (not owner) using O(1) index lookup
     * @param playerUUID The player's UUID
     * @return List of rentals where player is a member
     */
    public List<Rental> getRentalsWhereMember(UUID playerUUID) {
        Set<String> memberKeys = memberRentalIndex.get(playerUUID);
        if (memberKeys == null || memberKeys.isEmpty()) {
            return new ArrayList<>();
        }

        List<Rental> result = new ArrayList<>(memberKeys.size());
        for (String compositeKey : memberKeys) {
            Rental rental = rentals.get(compositeKey);
            if (rental != null) {
                result.add(rental);
            }
        }
        return result;
    }

    public List<Rental> getAllRentals() {
        return new ArrayList<>(rentals.values());
    }
    
    public List<Rental> getExpiredRentals() {
        return rentals.values().stream()
                .filter(Rental::isExpired)
                .collect(Collectors.toList());
    }
    
    public Map<String, Rental> getRentalsMap() {
        return new HashMap<>(rentals);
    }

    // Member management methods

    /**
     * Adds a member to a rental
     * @param regionName Region name
     * @param world World the region is in
     * @param renterUUID UUID of the renter (must own the rental)
     * @param memberUUID UUID of the member to add
     * @return true if member was added successfully
     */
    public boolean addMemberToRental(String regionName, org.bukkit.World world, UUID renterUUID, UUID memberUUID) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);

        // Check if rental exists and renter owns it
        if (rental == null || !rental.getPlayerUUID().equals(renterUUID)) {
            return false;
        }

        // Check if member already exists
        if (rental.hasMember(memberUUID)) {
            return false;
        }

        // Check member limit
        int maxMembers = plugin.getConfigManager().getMaxMembers();
        if (maxMembers != -1 && rental.getMemberCount() >= maxMembers) {
            return false;
        }

        // Add member to rental and WorldGuard region
        rental.addMember(memberUUID);
        plugin.getWorldGuardManager().addPlayerToRegion(regionName, world, memberUUID);

        // Update member index
        addToMemberIndex(memberUUID, compositeKey);

        markDirty(compositeKey);

        return true;
    }

    /**
     * Removes a member from a rental
     * @param regionName Region name
     * @param world World the region is in
     * @param renterUUID UUID of the renter (must own the rental)
     * @param memberUUID UUID of the member to remove
     * @return true if member was removed successfully
     */
    public boolean removeMemberFromRental(String regionName, org.bukkit.World world, UUID renterUUID, UUID memberUUID) {
        String compositeKey = world.getName() + ":" + regionName;
        Rental rental = rentals.get(compositeKey);

        // Check if rental exists and renter owns it
        if (rental == null || !rental.getPlayerUUID().equals(renterUUID)) {
            return false;
        }

        // Check if member exists
        if (!rental.hasMember(memberUUID)) {
            return false;
        }

        // Remove member from rental and WorldGuard region
        rental.removeMember(memberUUID);
        plugin.getWorldGuardManager().removePlayerFromRegion(regionName, world, memberUUID);

        // Update member index
        removeFromMemberIndex(memberUUID, compositeKey);

        markDirty(compositeKey);

        return true;
    }
}
