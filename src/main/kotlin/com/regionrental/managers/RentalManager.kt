package com.regionrental.managers

import com.regionrental.RegionRental
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Manages all rental operations including creation, extension, expiration, and persistence.
 */
class RentalManager(private val plugin: RegionRental) {

    private val rentals: MutableMap<String, Rental> = ConcurrentHashMap()
    private lateinit var rentalsFile: File
    private lateinit var rentalsConfig: YamlConfiguration

    // Dirty tracking for optimized saves
    @Volatile
    var isDirty: Boolean = false
        private set
    private val modifiedRentals: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Secondary indexes for O(1) player/member lookups
    private val playerRentalIndex: MutableMap<UUID, MutableSet<String>> = ConcurrentHashMap()
    private val memberRentalIndex: MutableMap<UUID, MutableSet<String>> = ConcurrentHashMap()

    init {
        setupRentalsFile()
    }

    private fun setupRentalsFile() {
        rentalsFile = File(plugin.dataFolder, "rentals.yml")

        if (!rentalsFile.exists()) {
            rentalsFile.parentFile?.mkdirs()
            try {
                rentalsFile.createNewFile()
                plugin.logger.info("Created rentals.yml file")
            } catch (e: IOException) {
                plugin.logger.log(Level.SEVERE, "Could not create rentals.yml!", e)
            }
        }

        rentalsConfig = YamlConfiguration.loadConfiguration(rentalsFile)
    }

    fun loadAllRentals() {
        rentals.clear()
        playerRentalIndex.clear()
        memberRentalIndex.clear()

        val rentalsSection = rentalsConfig.getConfigurationSection("rentals") ?: return

        var migratedCount = 0
        val defaultWorld = plugin.server.worlds[0].name

        for (configKey in rentalsSection.getKeys(false)) {
            val path = "rentals.$configKey"

            try {
                val playerUUID = UUID.fromString(rentalsConfig.getString("$path.player-uuid"))
                val playerName = rentalsConfig.getString("$path.player-name") ?: "Unknown"
                val startDate = rentalsConfig.getLong("$path.start-date")
                val endDate = rentalsConfig.getLong("$path.end-date")
                val extensionCount = rentalsConfig.getInt("$path.extension-count", 0)
                val totalPaid = rentalsConfig.getDouble("$path.total-paid", 0.0)
                val initialPrice = rentalsConfig.getDouble("$path.initial-price", totalPaid)

                // Load world field (with migration for old data)
                var worldName = rentalsConfig.getString("$path.world")
                var regionName = rentalsConfig.getString("$path.region-name")

                if (regionName == null) {
                    regionName = configKey
                }

                if (worldName == null) {
                    worldName = defaultWorld
                    migratedCount++
                }

                // Load refund tracking data
                val totalRefunded = rentalsConfig.getDouble("$path.total-refunded", 0.0)
                val refundHistory = mutableListOf<Rental.RefundRecord>()

                if (rentalsConfig.contains("$path.refund-history")) {
                    val refundList = rentalsConfig.getMapList("$path.refund-history")
                    for (refundData in refundList) {
                        val amount = (refundData["amount"] as Number).toDouble()
                        val timestamp = (refundData["timestamp"] as Number).toLong()
                        val reason = refundData["reason"] as String
                        val adminName = refundData["admin"] as String

                        refundHistory.add(Rental.RefundRecord(amount, timestamp, reason, adminName))
                    }
                }

                // Load members
                val members = mutableSetOf<UUID>()
                if (rentalsConfig.contains("$path.members")) {
                    val memberList = rentalsConfig.getStringList("$path.members")
                    for (uuidStr in memberList) {
                        try {
                            members.add(UUID.fromString(uuidStr))
                        } catch (e: IllegalArgumentException) {
                            plugin.logger.warning("Invalid member UUID in rental $configKey: $uuidStr")
                        }
                    }
                }

                val rental = Rental.fromStorage(
                    regionName, worldName, playerUUID, playerName, startDate, endDate,
                    extensionCount, totalPaid, initialPrice, totalRefunded, refundHistory, members
                )

                val compositeKey = rental.compositeKey
                rentals[compositeKey] = rental

                // Update secondary indexes
                addToPlayerIndex(playerUUID, compositeKey)
                for (memberUUID in members) {
                    addToMemberIndex(memberUUID, compositeKey)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load rental for config key $configKey: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${rentals.size} rentals")

        if (migratedCount > 0) {
            plugin.logger.info("Migrated $migratedCount rental(s) to multi-world format (defaulted to '$defaultWorld')")
            saveAllRentals()
        }
    }

    private fun markDirty(compositeKey: String?) {
        isDirty = true
        compositeKey?.let { modifiedRentals.add(it) }
    }

    // ========== Secondary Index Management ==========

    private fun addToPlayerIndex(playerUUID: UUID, compositeKey: String) {
        playerRentalIndex.computeIfAbsent(playerUUID) { ConcurrentHashMap.newKeySet() }.add(compositeKey)
    }

    private fun removeFromPlayerIndex(playerUUID: UUID, compositeKey: String) {
        playerRentalIndex[playerUUID]?.let { rentalsSet ->
            rentalsSet.remove(compositeKey)
            if (rentalsSet.isEmpty()) {
                playerRentalIndex.remove(playerUUID)
            }
        }
    }

    private fun addToMemberIndex(memberUUID: UUID, compositeKey: String) {
        memberRentalIndex.computeIfAbsent(memberUUID) { ConcurrentHashMap.newKeySet() }.add(compositeKey)
    }

    private fun removeFromMemberIndex(memberUUID: UUID, compositeKey: String) {
        memberRentalIndex[memberUUID]?.let { rentalsSet ->
            rentalsSet.remove(compositeKey)
            if (rentalsSet.isEmpty()) {
                memberRentalIndex.remove(memberUUID)
            }
        }
    }

    private fun removeAllMembersFromIndex(rental: Rental) {
        val compositeKey = rental.compositeKey
        for (memberUUID in rental.getMembers()) {
            removeFromMemberIndex(memberUUID, compositeKey)
        }
    }

    fun saveIfDirty() {
        if (isDirty) {
            saveAllRentals()
        }
    }

    fun saveAllRentals() {
        if (!isDirty && modifiedRentals.isEmpty()) {
            return
        }

        rentalsConfig = YamlConfiguration()

        for ((_, rental) in rentals) {
            val path = "rentals.${rental.compositeKey}"

            rentalsConfig.set("$path.region-name", rental.regionName)
            rentalsConfig.set("$path.world", rental.worldName)
            rentalsConfig.set("$path.player-uuid", rental.playerUUID.toString())
            rentalsConfig.set("$path.player-name", rental.playerName)
            rentalsConfig.set("$path.start-date", rental.startDate)
            rentalsConfig.set("$path.end-date", rental.endDate)
            rentalsConfig.set("$path.extension-count", rental.extensionCount)
            rentalsConfig.set("$path.total-paid", rental.totalPaid)
            rentalsConfig.set("$path.initial-price", rental.initialPrice)
            rentalsConfig.set("$path.total-refunded", rental.totalRefunded)

            // Save refund history
            val refundHistoryData = rental.getRefundHistory().map { record ->
                mapOf(
                    "amount" to record.amount,
                    "timestamp" to record.timestamp,
                    "reason" to record.reason,
                    "admin" to record.adminName
                )
            }
            rentalsConfig.set("$path.refund-history", refundHistoryData)

            // Save members
            val memberUUIDs = rental.getMembers().map { it.toString() }
            rentalsConfig.set("$path.members", memberUUIDs)
        }

        try {
            rentalsConfig.save(rentalsFile)
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Could not save rentals.yml!", e)
            return
        }

        isDirty = false
        modifiedRentals.clear()
    }

    fun createRental(regionName: String, world: World, player: Player, days: Int, price: Double): Boolean {
        val compositeKey = "${world.name}:$regionName"

        if (isRented(regionName, world)) {
            return false
        }

        if (getPlayerRentals(player.uniqueId).size >= plugin.configManager.maxRentalsPerPlayer) {
            player.sendMessage(plugin.configManager.getMessage("max-rentals-reached"))
            return false
        }

        if (plugin.configManager.isBlockRestoration) {
            val captured = plugin.worldEditManager.captureRegion(regionName, world)
            if (!captured && plugin.configManager.isDebug) {
                plugin.logger.warning("Failed to capture region state for: $regionName in world ${world.name}")
            }
        }

        val endDate = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L)
        val rental = Rental.create(regionName, world.name, player.uniqueId, player.name, endDate, price)

        rentals[compositeKey] = rental
        addToPlayerIndex(player.uniqueId, compositeKey)
        plugin.worldGuardManager.addPlayerToRegion(regionName, world, player.uniqueId)
        plugin.signManager.markSignDirty(regionName, world)
        markDirty(compositeKey)

        return true
    }

    fun extendRental(regionName: String, world: World, player: Player, days: Int, price: Double): Boolean {
        val compositeKey = "${world.name}:$regionName"
        val rental = rentals[compositeKey]

        if (rental == null || rental.playerUUID != player.uniqueId) {
            return false
        }

        if (rental.extensionCount >= plugin.configManager.maxExtensions) {
            player.sendMessage(plugin.configManager.getMessage("max-extensions-reached"))
            return false
        }

        rental.extendRental(days, price)
        plugin.signManager.markSignDirty(regionName, world)
        markDirty(compositeKey)

        return true
    }

    fun expireRental(regionName: String, world: World) {
        val compositeKey = "${world.name}:$regionName"
        val rental = rentals[compositeKey] ?: return

        plugin.worldGuardManager.removePlayerFromRegion(regionName, world, rental.playerUUID)

        removeAllMembersFromIndex(rental)
        for (memberUUID in rental.getMembers()) {
            plugin.worldGuardManager.removePlayerFromRegion(regionName, world, memberUUID)
        }
        rental.clearMembers()

        if (plugin.configManager.isItemStorage) {
            plugin.storageManager.storeItemsAndBlocksFromRegion(regionName, world, rental.playerUUID)
        }

        plugin.ezChestShopManager?.let { ezManager ->
            if (ezManager.isEnabled) {
                val shopsRemoved = ezManager.removeShopsInRegion(regionName, world)

                if (shopsRemoved > 0 && plugin.configManager.isEzChestShopNotifyOnRemoval) {
                    Bukkit.getPlayer(rental.playerUUID)?.let { player ->
                        if (player.isOnline) {
                            val message = ChatColor.translateAlternateColorCodes(
                                '&',
                                plugin.configManager.prefix + plugin.configManager.ezChestShopRemovalMessage
                                    .replace("{region}", regionName)
                            )
                            player.sendMessage(message)
                        }
                    }
                }
            }
        }

        if (plugin.configManager.isBlockRestoration) {
            val restored = plugin.worldEditManager.restoreRegion(regionName, world)
            if (restored) {
                plugin.logger.info("Restored blocks for region: $regionName in world ${world.name}")
            } else if (plugin.configManager.isDebug) {
                plugin.logger.warning("Failed to restore blocks for region: $regionName in world ${world.name}")
            }
        }

        removeFromPlayerIndex(rental.playerUUID, compositeKey)
        rentals.remove(compositeKey)
        plugin.signManager.markSignDirty(regionName, world)
        markDirty(null)

        plugin.logger.info("Rental expired for region $regionName in world ${world.name}")
    }

    fun issueRefund(rental: Rental?, amount: Double, reason: String, adminName: String): Map<String, Any>? {
        if (rental == null || amount <= 0) {
            return null
        }

        val maxRefund = rental.netRefundableAmount
        val actualRefund = minOf(amount, maxRefund)

        if (actualRefund <= 0) {
            return mapOf(
                "success" to false,
                "actualAmount" to 0.0,
                "message" to "No refundable amount remaining"
            )
        }

        val economy = plugin.economy ?: return null

        economy.depositPlayer(plugin.server.getOfflinePlayer(rental.playerUUID), actualRefund)
        rental.recordRefund(actualRefund, reason, adminName)
        markDirty(rental.compositeKey)

        val formattedAmount = String.format(plugin.configManager.currencyFormat, actualRefund)
        plugin.logger.info("Refund issued to ${rental.playerName} for ${rental.regionName}: $formattedAmount (Reason: $reason by $adminName)")

        plugin.server.getPlayer(rental.playerUUID)?.let { player ->
            if (player.isOnline) {
                player.sendMessage(
                    plugin.configManager.getMessage("refund-issued",
                        "{player}", rental.playerName,
                        "{amount}", formattedAmount,
                        "{reason}", reason)
                )
            }
        }

        return mapOf(
            "success" to true,
            "actualAmount" to actualRefund,
            "message" to "Refund issued successfully"
        )
    }

    fun calculateProportionalRefund(rental: Rental?, daysRemoved: Int): Double {
        if (rental == null || daysRemoved <= 0) {
            return 0.0
        }

        val totalDuration = rental.endDate - rental.startDate
        val daysInMs = daysRemoved * 24L * 60L * 60L * 1000L
        val proportion = minOf(1.0, daysInMs.toDouble() / totalDuration.toDouble())
        val netPaid = rental.totalPaid - rental.totalRefunded

        return maxOf(0.0, netPaid * proportion)
    }

    fun chargeDurationAdd(rental: Rental?, days: Int, player: Player?): Boolean {
        if (rental == null || days <= 0 || player == null) {
            return false
        }

        val world = plugin.server.getWorld(rental.worldName) ?: return false
        val extensionPrice = plugin.configManager.getExtensionPrice(rental.regionName, world)
        val totalCost = extensionPrice * days

        val economy = plugin.economy ?: return false

        if (economy.getBalance(player) < totalCost) {
            return false
        }

        economy.withdrawPlayer(player, totalCost)
        rental.addTimeWithCharge(days, totalCost)
        markDirty(rental.compositeKey)

        world?.let { plugin.signManager.markSignDirty(rental.regionName, it) }

        val formattedAmount = String.format(plugin.configManager.currencyFormat, totalCost)
        plugin.logger.info("Player ${player.name} charged $formattedAmount for $days days added to ${rental.regionName}")

        return true
    }

    fun resetRentalWithRefund(regionName: String, world: World): Map<String, Any>? {
        val compositeKey = "${world.name}:$regionName"
        val rental = rentals[compositeKey] ?: return null

        val playerUUID = rental.playerUUID
        val playerName = rental.playerName
        val totalPaid = rental.totalPaid
        val alreadyRefunded = rental.totalRefunded
        val netRefund = rental.netRefundableAmount

        if (netRefund > 0) {
            val refundResult = issueRefund(rental, netRefund, "admin_reset", "Admin")

            if (refundResult != null && refundResult["success"] == true) {
                val actualRefund = refundResult["actualAmount"] as Double

                plugin.server.getPlayer(playerUUID)?.let { player ->
                    if (player.isOnline) {
                        val formattedAmount = String.format(plugin.configManager.currencyFormat, actualRefund)
                        player.sendMessage(
                            plugin.configManager.getMessage("rental-reset-refund",
                                "{region}", regionName,
                                "{amount}", formattedAmount)
                        )
                    }
                }
            }
        }

        expireRental(regionName, world)

        return mapOf(
            "playerUUID" to playerUUID,
            "playerName" to playerName,
            "refundAmount" to netRefund,
            "totalPaid" to totalPaid,
            "alreadyRefunded" to alreadyRefunded
        )
    }

    fun resetRentalTime(regionName: String, world: World, days: Int) {
        val compositeKey = "${world.name}:$regionName"
        rentals[compositeKey]?.let { rental ->
            rental.resetTime(days)
            plugin.signManager.updateSign(regionName, world)
            markDirty(compositeKey)
        }
    }

    fun isRented(regionName: String, world: World): Boolean {
        val compositeKey = "${world.name}:$regionName"
        return compositeKey in rentals
    }

    fun getRental(regionName: String, world: World): Rental? {
        val compositeKey = "${world.name}:$regionName"
        return rentals[compositeKey]
    }

    fun getPlayerRentals(playerUUID: UUID): List<Rental> {
        val playerKeys = playerRentalIndex[playerUUID] ?: return emptyList()
        return playerKeys.mapNotNull { rentals[it] }
    }

    fun getRentalsWhereMember(playerUUID: UUID): List<Rental> {
        val memberKeys = memberRentalIndex[playerUUID] ?: return emptyList()
        return memberKeys.mapNotNull { rentals[it] }
    }

    val allRentals: List<Rental>
        get() = rentals.values.toList()

    val expiredRentals: List<Rental>
        get() = rentals.values.filter { it.isExpired }

    val rentalsMap: Map<String, Rental>
        get() = rentals.toMap()

    // Member management methods

    fun addMemberToRental(regionName: String, world: World, renterUUID: UUID, memberUUID: UUID): Boolean {
        val compositeKey = "${world.name}:$regionName"
        val rental = rentals[compositeKey]

        if (rental == null || rental.playerUUID != renterUUID) {
            return false
        }

        if (rental.hasMember(memberUUID)) {
            return false
        }

        val maxMembers = plugin.configManager.maxMembers
        if (maxMembers != -1 && rental.memberCount >= maxMembers) {
            return false
        }

        rental.addMember(memberUUID)
        plugin.worldGuardManager.addPlayerToRegion(regionName, world, memberUUID)
        addToMemberIndex(memberUUID, compositeKey)
        markDirty(compositeKey)

        return true
    }

    fun removeMemberFromRental(regionName: String, world: World, renterUUID: UUID, memberUUID: UUID): Boolean {
        val compositeKey = "${world.name}:$regionName"
        val rental = rentals[compositeKey]

        if (rental == null || rental.playerUUID != renterUUID) {
            return false
        }

        if (!rental.hasMember(memberUUID)) {
            return false
        }

        rental.removeMember(memberUUID)
        plugin.worldGuardManager.removePlayerFromRegion(regionName, world, memberUUID)
        removeFromMemberIndex(memberUUID, compositeKey)
        markDirty(compositeKey)

        return true
    }
}
