package com.zonerental.config

import com.zonerental.ZoneRental
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: ZoneRental) {

    private var config: FileConfiguration = plugin.config

    // Cached values
    var prefix: String = ""
        private set
    var commandPrefix: String = "zr"
        private set
    var isDebug: Boolean = false
        private set
    var expirationCheckInterval: Int = 1
        private set

    // Economy settings
    var isEconomyEnabled: Boolean = true
        private set
    var defaultPrice: Double = 100.0
        private set
    var currencyFormat: String = "$%.2f"
        private set
    private var regionPrices: MutableMap<String, Double> = mutableMapOf()
    var permissionPrices: Map<String, Double> = emptyMap()
        private set

    // Durations
    var defaultDuration: Int = 7
        private set
    var extensionDuration: Int = 7
        private set
    var maxExtensions: Int = 10
        private set
    var availableDurations: List<Int> = listOf(1, 3, 7, 14, 30)
        private set

    // Limits
    var maxRentalsPerPlayer: Int = 3
        private set

    // Messages
    private var messages: MutableMap<String, String> = mutableMapOf()

    // Sign formats
    var availableSignFormat: List<String> = emptyList()
        private set
    var rentedSignFormat: List<String> = emptyList()
        private set

    // Features
    var isBlockRestoration: Boolean = true
        private set
    var isAutoDeleteSchematics: Boolean = true
        private set
    var isItemStorage: Boolean = true
        private set
    var isSignProtection: Boolean = true
        private set

    // Teleport settings
    var isTeleportEnabled: Boolean = true
        private set
    var teleportMaxSearchDistance: Int = 20
        private set
    var teleportForwardSearchDistance: Int = 5
        private set
    var teleportFloorSearchDown: Int = 20
        private set
    var teleportFloorSearchUp: Int = 20
        private set
    var teleportCooldown: Int = 30
        private set
    var isTeleportCrossWorldWarning: Boolean = true
        private set
    var isTeleportSoundEnabled: Boolean = true
        private set
    var isTeleportParticleEnabled: Boolean = true
        private set

    init {
        loadConfig()
    }

    fun reload() {
        plugin.reloadConfig()
        config = plugin.config
        loadConfig()
    }

    private fun loadConfig() {
        // General settings
        prefix = config.getString("general.prefix", "&8[&6ZoneRental&8]&r ")?.color() ?: ""
        isDebug = config.getBoolean("general.debug", false)
        expirationCheckInterval = config.getInt("general.expiration-check-interval", 1)

        // Command prefix setting
        val configuredPrefix = config.getString("commands.prefix", "rr") ?: "rr"
        commandPrefix = validatePrefix(configuredPrefix)

        // Economy settings
        isEconomyEnabled = config.getBoolean("economy.enabled", true)
        defaultPrice = config.getDouble("economy.default-price", 100.0)
        currencyFormat = config.getString("economy.currency-format", "$%.2f") ?: "$%.2f"

        // Load per-region prices
        regionPrices = mutableMapOf()
        config.getConfigurationSection("regions")?.getKeys(false)?.forEach { region ->
            val price = config.getDouble("regions.$region.price", defaultPrice)
            regionPrices[region] = price
        }

        // Load permission-based prices
        val permPrices = mutableMapOf<String, Double>()
        config.getConfigurationSection("permission-prices")?.getKeys(false)?.forEach { perm ->
            val price = config.getDouble("permission-prices.$perm", defaultPrice)
            permPrices[perm] = price
        }
        permissionPrices = permPrices

        // Duration settings
        defaultDuration = config.getInt("durations.default-days", 7)
        extensionDuration = config.getInt("extension.extension-days", 7)
        maxExtensions = config.getInt("extension.max-extensions", 10)
        availableDurations = config.getIntegerList("durations.available-durations").ifEmpty {
            listOf(1, 3, 7, 14, 30)
        }

        // Limits
        maxRentalsPerPlayer = config.getInt("limits.max-rentals-per-player", 3)

        // Load messages
        loadMessages()

        // Sign formats
        availableSignFormat = config.getStringList("signs.available-format").ifEmpty {
            listOf(
                "&2[AVAILABLE]",
                "&6{region}",
                "&e{price}",
                "&7{duration} days"
            )
        }

        rentedSignFormat = config.getStringList("signs.rented-format").ifEmpty {
            listOf(
                "&c[RENTED]",
                "&6{region}",
                "&e{owner}",
                "&7Exp: {expires}"
            )
        }

        // Features
        isBlockRestoration = config.getBoolean("restoration.enabled", true)
        isAutoDeleteSchematics = config.getBoolean("restoration.auto-delete-schematics", true)
        isItemStorage = config.getBoolean("storage.enabled", true)
        isSignProtection = config.getBoolean("signs.protect-signs", true)

        // Teleport settings
        isTeleportEnabled = config.getBoolean("teleport.enabled", true)
        teleportMaxSearchDistance = config.getInt("teleport.max-search-distance", 20)
        teleportForwardSearchDistance = minOf(config.getInt("teleport.forward-search-distance", 5), 20)
        teleportFloorSearchDown = minOf(config.getInt("teleport.floor-search-down", 20), 20)
        teleportFloorSearchUp = config.getInt("teleport.floor-search-up", 20)
        teleportCooldown = config.getInt("teleport.cooldown", 30)
        isTeleportCrossWorldWarning = config.getBoolean("teleport.cross-world-warning", true)
        isTeleportSoundEnabled = config.getBoolean("teleport.sound-enabled", true)
        isTeleportParticleEnabled = config.getBoolean("teleport.particle-enabled", true)
    }

    private fun loadMessages() {
        messages = mutableMapOf(
            "no-permission" to "&cYou don't have permission to do that!",
            "region-not-found" to "&cRegion &e{region}&c not found!",
            "already-rented" to "&cThis region is already rented!",
            "rental-success" to "&aYou have successfully rented &e{region}&a for &e{days}&a days!",
            "rental-expired" to "&cYour rental of &e{region}&c has expired!",
            "not-enough-money" to "&cYou don't have enough money! Need &e{amount}",
            "max-rentals-reached" to "&cYou have reached the maximum number of rentals!",
            "rental-extended" to "&aRental extended for &e{days}&a days!",
            "max-extensions-reached" to "&cMaximum extensions reached for this rental!",
            "items-stored" to "&aYour items from &e{region}&a have been stored!",
            "items-retrieved" to "&aYou have retrieved your stored items!",
            "no-stored-items" to "&cYou have no stored items!",
            "sign-created" to "&aRental sign created for region &e{region}!",
            "sign-removed" to "&aRental sign removed!",
            "rental-reset" to "&aRental for &e{region}&a has been reset!",
            "admin-reset-success" to "&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.",
            "rental-reset-refund" to "&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.",
            "region-removed" to "&aZoneRental setup completely removed from &e{region}&a:",
            "rental-info" to "&6=== Rental Info for {region} ===",
            "config-reloaded" to "&aConfiguration reloaded!",
            // Refund tracking messages
            "refund-issued" to "&aRefund issued to &e{player}&a: &e{amount}&a (Reason: {reason})",
            "refund-already-given" to "&cCannot refund. Total refunded (&e{refunded}&c) would exceed total paid (&e{paid}&c).",
            "refund-history-header" to "&a&lRefund History for &e{region}",
            "refund-partial" to "&aProportional refund for &e{days}&a days removed: &e{amount}",
            // Duration command messages
            "duration-add-charged" to "&aAdded &e{days}&a days to &e{region}&a. Player &e{player}&a was charged &e{amount}&a.",
            "duration-add-free" to "&aAdded &e{days}&a to &e{region}&a (no charge).",
            "duration-remove-refunded" to "&aRemoved &e{days}&a days from &e{region}&a. Player &e{player}&a was refunded &e{amount}&a.",
            "duration-remove-no-refund" to "&aRemoved &e{days}&a days from &e{region}&a (no refund issued)."
        )

        // Override with config values
        config.getConfigurationSection("messages")?.getKeys(false)?.forEach { key ->
            config.getString("messages.$key")?.let { messages[key] = it }
        }
    }

    fun getMessage(key: String, vararg replacements: String): String {
        var message = messages.getOrDefault(key, "&cMissing message: $key")
        message = prefix + message

        // Replace placeholders
        var i = 0
        while (i < replacements.size - 1) {
            message = message.replace(replacements[i], replacements[i + 1])
            i += 2
        }

        return message.color()
    }

    private fun String.color(): String = ChatColor.translateAlternateColorCodes('&', this)

    /**
     * Validates and sanitizes the command prefix.
     * Rules:
     * - Lowercase only (auto-converts)
     * - Alphanumeric only (a-z, 0-9)
     * - Length: 2-10 characters
     * - Defaults to "rr" if invalid
     */
    private fun validatePrefix(prefix: String): String {
        if (prefix.isEmpty()) {
            plugin.logger.warning("Command prefix is empty! Defaulting to 'rr'")
            return "rr"
        }

        val validated = prefix.lowercase()

        if (validated.length < 2 || validated.length > 10) {
            plugin.logger.warning("Command prefix '$prefix' is invalid (must be 2-10 characters). Defaulting to 'rr'")
            return "rr"
        }

        if (!validated.matches(Regex("[a-z0-9]+"))) {
            plugin.logger.warning("Command prefix '$prefix' is invalid (must be lowercase letters/numbers only). Defaulting to 'rr'")
            return "rr"
        }

        if (validated != prefix) {
            plugin.logger.info("Command prefix '$prefix' converted to lowercase: '$validated'")
        }

        return validated
    }

    fun getPriceForRegion(region: String, world: World): Double {
        plugin.regionsConfig?.let {
            return it.getRegionPrice(region, world, defaultPrice)
        }
        return regionPrices.getOrDefault(region, defaultPrice)
    }

    fun getDurationForRegion(region: String, world: World): Int {
        plugin.regionsConfig?.let {
            return it.getRegionDuration(region, world, defaultDuration)
        }
        return config.getInt("regions.$region.duration", defaultDuration)
    }

    fun getMaxExtensionsForRegion(region: String, world: World): Int {
        plugin.regionsConfig?.let {
            return it.getRegionMaxExtensions(region, world, maxExtensions)
        }
        return maxExtensions
    }

    fun getExtensionPriceForRegion(region: String, world: World): Double {
        val calculatedPrice = getExtensionPrice(region, world)
        plugin.regionsConfig?.let {
            return it.getRegionExtensionPrice(region, world, calculatedPrice)
        }
        return calculatedPrice
    }

    fun getAllowExtensionsForRegion(region: String, world: World): Boolean {
        val defaultAllow = config.getBoolean("extension.enabled", true)
        plugin.regionsConfig?.let {
            return it.getRegionAllowExtensions(region, world, defaultAllow)
        }
        return defaultAllow
    }

    fun getExtensionDurationForRegion(region: String, world: World): Int {
        plugin.regionsConfig?.let {
            return it.getRegionExtensionDuration(region, world, extensionDuration)
        }
        return extensionDuration
    }

    // Config-backed properties (read directly from config)
    val isRefundOnDurationReset: Boolean
        get() = config.getBoolean("extension.refund-on-duration-reset", true)

    fun getSchematicCacheSize(defaultSize: Int): Int =
        config.getInt("restoration.schematic-cache-size", defaultSize)

    val isBlockStorage: Boolean
        get() = config.getBoolean("storage.store-player-blocks", true)

    val blockBlacklist: List<String>
        get() = config.getStringList("storage.block-blacklist")

    // Member management settings
    val isMemberManagementEnabled: Boolean
        get() = config.getBoolean("members.enabled", true)

    val maxMembers: Int
        get() = config.getInt("members.max-members", 5)

    // Duration-related refund and charge settings
    val isRefundOnTimeRemoval: Boolean
        get() = config.getBoolean("duration.refund-on-time-removal", true)

    val isChargeForDurationAdd: Boolean
        get() = config.getBoolean("duration.charge-for-add", false)

    val isDurationAddBypassExtensionLimit: Boolean
        get() = config.getBoolean("duration.add-bypass-extension-limit", true)

    val durationAddPricePerDay: Double
        get() = config.getDouble("duration.add-price-per-day", 0.0)

    // Extension price calculation helper
    fun getExtensionPrice(region: String, world: World): Double {
        val pricePerDay = durationAddPricePerDay
        if (pricePerDay > 0) {
            return pricePerDay
        }

        val regionPrice = getPriceForRegion(region, world)
        val duration = getDurationForRegion(region, world)

        if (duration <= 0) {
            return regionPrice
        }

        return regionPrice / duration
    }

    // Region verification settings
    val isAutoVerifyRegions: Boolean
        get() = config.getBoolean("regions-config.auto-verify-regions", true)

    val isEnableVerifyCommand: Boolean
        get() = config.getBoolean("regions-config.enable-verify-command", true)

    // EzChestShop integration settings
    val isEzChestShopEnabled: Boolean
        get() = config.getBoolean("integration.ezchestshop.enabled", true)

    val isEzChestShopNotifyOnRemoval: Boolean
        get() = config.getBoolean("integration.ezchestshop.notify-on-removal", true)

    val ezChestShopRemovalMessage: String
        get() = config.getString("integration.ezchestshop.removal-message",
            "&eChest shops in &6{region}&e have been removed due to rental expiration.") ?: ""

    // Aliases for enhanced EzChestShop manager
    val isEzChestShopRemovalEnabled: Boolean
        get() = isEzChestShopEnabled

    val isEzChestShopNotifyEnabled: Boolean
        get() = isEzChestShopNotifyOnRemoval

    val isDebugMode: Boolean
        get() = isDebug
}
