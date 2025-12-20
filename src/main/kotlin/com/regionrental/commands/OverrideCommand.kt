package com.regionrental.commands

import com.regionrental.RegionRental
import com.regionrental.models.ParsedRegion
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Sealed class representing the different override setting types.
 * Each type knows how to parse its value and apply the override.
 */
sealed class OverrideSetting<T> {
    abstract val name: String
    abstract val usage: String
    abstract val examples: List<String>

    abstract fun parseValue(input: String): ParseResult<T>
    abstract fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: T)
    abstract fun applyToGroup(plugin: RegionRental, groupName: String, value: T)
    abstract fun formatSuccess(value: T, plugin: RegionRental): String

    sealed class ParseResult<out T> {
        data class Success<T>(val value: T) : ParseResult<T>()
        data class Error(val message: String) : ParseResult<Nothing>()
    }

    data object Price : OverrideSetting<Double>() {
        override val name = "price"
        override val usage = "/rroverride price <group:group_name|world:region> <amount>"
        override val examples = listOf(
            "/rroverride price group:shop_group 500",
            "/rroverride price world:shop1 500"
        )

        override fun parseValue(input: String): ParseResult<Double> {
            val value = input.toDoubleOrNull()
                ?: return ParseResult.Error("Invalid price amount!")
            if (value < 0) return ParseResult.Error("Price must be a positive number!")
            return ParseResult.Success(value)
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Double) {
            plugin.regionsConfig.setRegionPrice(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Double) {
            plugin.regionsConfig.setGroupPrice(groupName, value)
        }

        override fun formatSuccess(value: Double, plugin: RegionRental): String {
            val formatted = String.format(plugin.configManager.currencyFormat, value)
            return "rental price to ${ChatColor.GOLD}$formatted"
        }
    }

    data object Duration : OverrideSetting<Int>() {
        override val name = "duration"
        override val usage = "/rroverride duration <group:group_name|world:region> <days>"
        override val examples = emptyList<String>()

        override fun parseValue(input: String): ParseResult<Int> {
            val value = input.toIntOrNull()
                ?: return ParseResult.Error("Invalid duration!")
            if (value <= 0) return ParseResult.Error("Duration must be a positive number!")
            return ParseResult.Success(value)
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Int) {
            plugin.regionsConfig.setRegionDuration(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Int) {
            plugin.regionsConfig.setGroupDuration(groupName, value)
        }

        override fun formatSuccess(value: Int, plugin: RegionRental): String =
            "rental duration to ${ChatColor.GOLD}$value days"
    }

    data object MaxExtensions : OverrideSetting<Int>() {
        override val name = "maxextensions"
        override val usage = "/rroverride maxextensions <group:group_name|world:region> <count>"
        override val examples = emptyList<String>()

        override fun parseValue(input: String): ParseResult<Int> {
            val value = input.toIntOrNull()
                ?: return ParseResult.Error("Invalid number!")
            if (value < 0) return ParseResult.Error("Max extensions must be 0 or greater!")
            return ParseResult.Success(value)
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Int) {
            plugin.regionsConfig.setRegionMaxExtensions(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Int) {
            plugin.regionsConfig.setGroupMaxExtensions(groupName, value)
        }

        override fun formatSuccess(value: Int, plugin: RegionRental): String =
            "max extensions to ${ChatColor.GOLD}$value"
    }

    data object ExtensionPrice : OverrideSetting<Double>() {
        override val name = "extensionprice"
        override val usage = "/rroverride extensionprice <group:group_name|world:region> <amount>"
        override val examples = listOf("Tip: Use 0 to auto-calculate from rental price and duration")

        override fun parseValue(input: String): ParseResult<Double> {
            val value = input.toDoubleOrNull()
                ?: return ParseResult.Error("Invalid price amount!")
            if (value < 0) return ParseResult.Error("Extension price must be 0 or greater!")
            return ParseResult.Success(value)
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Double) {
            plugin.regionsConfig.setRegionExtensionPrice(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Double) {
            plugin.regionsConfig.setGroupExtensionPrice(groupName, value)
        }

        override fun formatSuccess(value: Double, plugin: RegionRental): String {
            val formatted = if (value == 0.0) "auto-calculated"
                else String.format(plugin.configManager.currencyFormat, value)
            return "extension price to ${ChatColor.GOLD}$formatted"
        }
    }

    data object AllowExtensions : OverrideSetting<Boolean>() {
        override val name = "allowextensions"
        override val usage = "/rroverride allowextensions <group:group_name|world:region> <true|false>"
        override val examples = emptyList<String>()

        override fun parseValue(input: String): ParseResult<Boolean> {
            return when (input.lowercase()) {
                "true" -> ParseResult.Success(true)
                "false" -> ParseResult.Success(false)
                else -> ParseResult.Error("Value must be 'true' or 'false'!")
            }
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Boolean) {
            plugin.regionsConfig.setRegionAllowExtensions(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Boolean) {
            plugin.regionsConfig.setGroupAllowExtensions(groupName, value)
        }

        override fun formatSuccess(value: Boolean, plugin: RegionRental): String =
            "allow extensions to ${ChatColor.GOLD}$value"
    }

    data object ExtensionDuration : OverrideSetting<Int>() {
        override val name = "extensionduration"
        override val usage = "/rroverride extensionduration <group:group_name|world:region> <days>"
        override val examples = emptyList<String>()

        override fun parseValue(input: String): ParseResult<Int> {
            val value = input.toIntOrNull()
                ?: return ParseResult.Error("Invalid duration!")
            if (value <= 0) return ParseResult.Error("Extension duration must be a positive number!")
            return ParseResult.Success(value)
        }

        override fun applyToRegion(plugin: RegionRental, regionName: String, world: World, value: Int) {
            plugin.regionsConfig.setRegionExtensionDuration(regionName, world, value)
        }

        override fun applyToGroup(plugin: RegionRental, groupName: String, value: Int) {
            plugin.regionsConfig.setGroupExtensionDuration(groupName, value)
        }

        override fun formatSuccess(value: Int, plugin: RegionRental): String =
            "extension duration to ${ChatColor.GOLD}$value days"
    }

    companion object {
        fun fromName(name: String): OverrideSetting<*>? = when (name.lowercase()) {
            "price" -> Price
            "duration" -> Duration
            "maxextensions" -> MaxExtensions
            "extensionprice" -> ExtensionPrice
            "allowextensions" -> AllowExtensions
            "extensionduration" -> ExtensionDuration
            else -> null
        }

        val all: List<OverrideSetting<*>> = listOf(
            Price, Duration, MaxExtensions, ExtensionPrice, AllowExtensions, ExtensionDuration
        )
    }
}

/**
 * Kotlin rewrite of OverrideCommand using sealed classes to eliminate duplicate handler methods.
 * Reduces ~860 lines of Java to ~350 lines of Kotlin.
 */
class OverrideCommand(private val plugin: RegionRental) : CommandExecutor, TabCompleter {

    companion object {
        private const val GROUP_CACHE_TTL = 5000L // 5 seconds
        private val SUB_COMMANDS = listOf(
            "price", "duration", "maxextensions", "extensionprice",
            "allowextensions", "extensionduration", "remove", "list"
        )
    }

    // Tab completion cache for group names
    private var cachedGroupNames: List<String>? = null
    private var groupCacheExpiry: Long = 0

    private fun getCachedGroupNames(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedGroupNames == null || now > groupCacheExpiry) {
            cachedGroupNames = plugin.groupsConfig.allGroups.toList()
            groupCacheExpiry = now + GROUP_CACHE_TTL
        }
        return cachedGroupNames!!
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("regionrental.admin.override")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (val subCommand = args[0].lowercase()) {
            "remove" -> handleRemove(sender, args)
            "list" -> handleList(sender, args)
            else -> {
                val setting = OverrideSetting.fromName(subCommand)
                if (setting != null) {
                    handleSetting(sender, args, setting)
                } else {
                    showUsage(sender)
                    true
                }
            }
        }
    }

    /**
     * Generic handler for all override settings.
     * This single method replaces 6 nearly identical handler methods from the Java version.
     */
    private fun <T> handleSetting(sender: CommandSender, args: Array<String>, setting: OverrideSetting<T>): Boolean {
        if (args.size != 3) {
            sender.sendMessage("${ChatColor.RED}Usage: ${setting.usage}")
            setting.examples.forEach { sender.sendMessage("${ChatColor.YELLOW}$it") }
            return true
        }

        val target = args[1]
        val valueInput = args[2]

        // Parse the value using the setting's parser
        val parseResult = setting.parseValue(valueInput)
        if (parseResult is OverrideSetting.ParseResult.Error) {
            sender.sendMessage("${ChatColor.RED}${parseResult.message}")
            return true
        }
        val value = (parseResult as OverrideSetting.ParseResult.Success<T>).value

        // Handle group target
        if (target.startsWith("group:")) {
            return handleGroupTarget(sender, target.substring(6), value, setting, valueInput)
        }

        // Handle region target
        return handleRegionTarget(sender, target, value, setting, valueInput)
    }

    private fun <T> handleGroupTarget(
        sender: CommandSender,
        groupName: String,
        value: T,
        setting: OverrideSetting<T>,
        rawValue: String
    ): Boolean {
        // Validate group exists
        if (!plugin.groupsConfig.groupExists(groupName)) {
            sender.sendMessage("${ChatColor.RED}Group '${ChatColor.YELLOW}$groupName${ChatColor.RED}' does not exist!")
            val allGroups = plugin.groupsConfig.allGroups
            if (allGroups.isNotEmpty()) {
                sender.sendMessage("${ChatColor.YELLOW}Available groups: ${allGroups.joinToString(", ")}")
            }
            return true
        }

        // Apply the override
        setting.applyToGroup(plugin, groupName, value)

        // Mark all signs in group as dirty
        val groupRegions = plugin.groupsConfig.getGroupRegions(groupName)
        plugin.signManager.bulkMarkSignsDirty(groupRegions)

        // Success message
        val successDesc = setting.formatSuccess(value, plugin)
        sender.sendMessage("${ChatColor.GREEN}Set $successDesc for group ${ChatColor.YELLOW}$groupName")
        sender.sendMessage("${ChatColor.GRAY}Updated ${groupRegions.size} region(s) in group")

        plugin.logger.info("${sender.name} set ${setting.name} override for group $groupName to $rawValue")
        return true
    }

    private fun <T> handleRegionTarget(
        sender: CommandSender,
        target: String,
        value: T,
        setting: OverrideSetting<T>,
        rawValue: String
    ): Boolean {
        // Parse region
        val parsed = ParsedRegion.parse(target, sender) ?: run {
            sender.sendMessage("${ChatColor.RED}Invalid target! Use format: world:region or group:group_name")
            return true
        }

        val world = parsed.world ?: run {
            sender.sendMessage("${ChatColor.RED}World '${parsed.worldName}' does not exist!")
            return true
        }
        val regionName = parsed.regionName

        // Validate region exists in WorldGuard
        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            sender.sendMessage("${ChatColor.RED}Region '$regionName' does not exist in world '${world.name}'!")
            return true
        }

        // Check if region is in a group
        val compositeKey = parsed.compositeKey
        if (plugin.groupsConfig.isRegionInGroup(compositeKey)) {
            val groupName = plugin.groupsConfig.getRegionGroup(compositeKey)
            sender.sendMessage("${ChatColor.RED}Region ${ChatColor.YELLOW}$compositeKey${ChatColor.RED} is in group ${ChatColor.YELLOW}$groupName")
            sender.sendMessage("${ChatColor.YELLOW}Use: /rroverride ${setting.name} group:$groupName $rawValue")
            return true
        }

        // Apply the override
        setting.applyToRegion(plugin, regionName, world, value)

        // Mark sign dirty
        plugin.signManager.markSignDirty(regionName, world)

        // Success message
        val successDesc = setting.formatSuccess(value, plugin)
        sender.sendMessage("${ChatColor.GREEN}Set $successDesc for ${ChatColor.YELLOW}$compositeKey")

        plugin.logger.info("${sender.name} set ${setting.name} override for region $compositeKey to $rawValue")
        return true
    }

    private fun handleRemove(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size != 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /rroverride remove <group:group_name|world:region>")
            return true
        }

        val target = args[1]

        // Handle group target
        if (target.startsWith("group:")) {
            val groupName = target.substring(6)

            if (!plugin.groupsConfig.groupExists(groupName)) {
                sender.sendMessage("${ChatColor.RED}Group '${ChatColor.YELLOW}$groupName${ChatColor.RED}' does not exist!")
                return true
            }

            if (!plugin.regionsConfig.hasGroupOverrides(groupName)) {
                sender.sendMessage("${ChatColor.RED}Group '$groupName' has no custom overrides!")
                return true
            }

            plugin.regionsConfig.removeGroupOverrides(groupName)

            val groupRegions = plugin.groupsConfig.getGroupRegions(groupName)
            plugin.signManager.bulkMarkSignsDirty(groupRegions)

            sender.sendMessage("${ChatColor.GREEN}Removed all custom overrides for group ${ChatColor.YELLOW}$groupName")
            sender.sendMessage("${ChatColor.YELLOW}Group will now use default settings from config.yml")
            sender.sendMessage("${ChatColor.GRAY}Updated ${groupRegions.size} region(s) in group")

            plugin.logger.info("${sender.name} removed overrides for group $groupName")
            return true
        }

        // Handle region target
        val parsed = ParsedRegion.parse(target, sender) ?: run {
            sender.sendMessage("${ChatColor.RED}Invalid target! Use format: world:region or group:group_name")
            return true
        }

        val world = parsed.world ?: run {
            sender.sendMessage("${ChatColor.RED}World '${parsed.worldName}' does not exist!")
            return true
        }
        val regionName = parsed.regionName

        if (!plugin.regionsConfig.hasRegion(regionName, world)) {
            sender.sendMessage("${ChatColor.RED}Region '${parsed.compositeKey}' has no custom overrides!")
            return true
        }

        plugin.regionsConfig.removeRegion(regionName, world)
        plugin.signManager.markSignDirty(regionName, world)

        sender.sendMessage("${ChatColor.GREEN}Removed all custom overrides for ${ChatColor.YELLOW}${parsed.compositeKey}")
        sender.sendMessage("${ChatColor.YELLOW}Region will now use default settings from config.yml")

        plugin.logger.info("${sender.name} removed overrides for region ${parsed.compositeKey}")
        return true
    }

    private fun handleList(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size == 1) {
            // List all groups and regions with overrides
            val groups = plugin.regionsConfig.allGroupsWithOverrides
            val regions = plugin.regionsConfig.allRegions

            if (groups.isEmpty() && regions.isEmpty()) {
                sender.sendMessage("${ChatColor.YELLOW}No groups or regions have custom overrides configured")
                sender.sendMessage("${ChatColor.GRAY}Use /rroverride <setting> <group|world:region> <value> to set overrides")
                return true
            }

            if (groups.isNotEmpty()) {
                sender.sendMessage("${ChatColor.GOLD}=== Groups with Custom Overrides ===")
                groups.forEach { group ->
                    val regionCount = plugin.groupsConfig.getGroupSize(group)
                    sender.sendMessage("${ChatColor.YELLOW}  - $group${ChatColor.GRAY} ($regionCount regions) ${ChatColor.DARK_GRAY}(/rroverride list group:$group)")
                }
            }

            if (regions.isNotEmpty()) {
                sender.sendMessage("${ChatColor.GOLD}=== Regions with Custom Overrides ===")
                regions.forEach { compositeKey ->
                    sender.sendMessage("${ChatColor.YELLOW}  - $compositeKey${ChatColor.DARK_GRAY} (/rroverride list $compositeKey)")
                }
            }

            sender.sendMessage("${ChatColor.GRAY}Total: ${groups.size} group(s), ${regions.size} region(s)")
            return true
        }

        if (args.size == 2) {
            val target = args[1]

            // Handle group target
            if (target.startsWith("group:")) {
                val groupName = target.substring(6)

                if (!plugin.groupsConfig.groupExists(groupName)) {
                    sender.sendMessage("${ChatColor.RED}Group '${ChatColor.YELLOW}$groupName${ChatColor.RED}' does not exist!")
                    return true
                }

                if (!plugin.regionsConfig.hasGroupOverrides(groupName)) {
                    sender.sendMessage("${ChatColor.RED}Group '$groupName' has no custom overrides")
                    sender.sendMessage("${ChatColor.YELLOW}Using default values from config.yml")
                    return true
                }

                sender.sendMessage("${ChatColor.GOLD}=== Custom Overrides for Group: $groupName ===")

                val overrides = plugin.regionsConfig.getGroupOverrides(groupName)
                if (overrides.isEmpty()) {
                    sender.sendMessage("${ChatColor.YELLOW}No overrides set (using defaults)")
                    return true
                }

                overrides.forEach { (key, value) ->
                    sender.sendMessage("${ChatColor.YELLOW}  $key: ${ChatColor.WHITE}$value")
                }

                val regionCount = plugin.groupsConfig.getGroupSize(groupName)
                sender.sendMessage("${ChatColor.GRAY}Applies to $regionCount region(s) in group")
                return true
            }

            // Handle region target
            val parsed = ParsedRegion.parse(target, sender) ?: run {
                sender.sendMessage("${ChatColor.RED}Invalid target! Use format: world:region or group:group_name")
                return true
            }

            val world = parsed.world ?: run {
                sender.sendMessage("${ChatColor.RED}World '${parsed.worldName}' does not exist!")
                return true
            }
            val regionName = parsed.regionName

            if (!plugin.regionsConfig.hasRegion(regionName, world)) {
                sender.sendMessage("${ChatColor.RED}Region '${parsed.compositeKey}' has no custom overrides")
                sender.sendMessage("${ChatColor.YELLOW}Using default values from config.yml")
                return true
            }

            sender.sendMessage("${ChatColor.GOLD}=== Custom Overrides for ${parsed.compositeKey} ===")

            val overrides = plugin.regionsConfig.getRegionOverrides(regionName, world)
            if (overrides.isEmpty()) {
                sender.sendMessage("${ChatColor.YELLOW}No overrides set (using defaults)")
                return true
            }

            overrides.filterKeys { it != "_comment" }.forEach { (key, value) ->
                sender.sendMessage("${ChatColor.YELLOW}  $key: ${ChatColor.WHITE}$value")
            }
            return true
        }

        sender.sendMessage("${ChatColor.RED}Usage: /rroverride list [group:group_name|world:region]")
        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}=== RegionRental Override Commands ===")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride price <group|region> <amount>")
        sender.sendMessage("${ChatColor.GRAY}  Set custom rental price for group or region")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride duration <group|region> <days>")
        sender.sendMessage("${ChatColor.GRAY}  Set custom rental duration")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride maxextensions <group|region> <count>")
        sender.sendMessage("${ChatColor.GRAY}  Set max number of extensions allowed")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride extensionprice <group|region> <amount>")
        sender.sendMessage("${ChatColor.GRAY}  Set extension price (0 for auto-calculate)")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride allowextensions <group|region> <true|false>")
        sender.sendMessage("${ChatColor.GRAY}  Enable or disable extensions")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride extensionduration <group|region> <days>")
        sender.sendMessage("${ChatColor.GRAY}  Set extension duration in days")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride remove <region>")
        sender.sendMessage("${ChatColor.GRAY}  Remove all region overrides (use defaults)")
        sender.sendMessage("${ChatColor.YELLOW}/rroverride list [group|region]")
        sender.sendMessage("${ChatColor.GRAY}  List all overrides or show details")
        sender.sendMessage("${ChatColor.GRAY}Note: Regions in groups must use group overrides")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("regionrental.admin.override")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                val partial = args[0].lowercase()
                SUB_COMMANDS.filter { it.startsWith(partial) }
            }
            2 -> {
                val partial = args[1].lowercase()
                buildList {
                    // Group suggestions
                    if (partial.startsWith("group:")) {
                        val groupPrefix = partial.substring(6)
                        getCachedGroupNames()
                            .filter { it.lowercase().startsWith(groupPrefix) }
                            .forEach { add("group:$it") }
                    } else {
                        if ("group:".startsWith(partial)) add("group:")
                        getCachedGroupNames()
                            .map { "group:$it" }
                            .filter { it.lowercase().startsWith(partial) }
                            .forEach { add(it) }

                        // World:region suggestions
                        if (sender is Player) {
                            val worldPrefix = "${sender.world.name}:"
                            if (worldPrefix.lowercase().startsWith(partial)) add(worldPrefix)
                        }

                        // Region names
                        plugin.worldGuardManager.allRegionNames
                            .filter { it.lowercase().startsWith(partial) }
                            .forEach { add(it) }
                    }
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "price", "extensionprice" -> listOf("10", "50", "100", "500", "1000")
                    "duration", "extensionduration" -> listOf("1", "3", "7", "14", "30")
                    "maxextensions" -> listOf("0", "5", "10", "20", "unlimited")
                    "allowextensions" -> listOf("true", "false")
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
