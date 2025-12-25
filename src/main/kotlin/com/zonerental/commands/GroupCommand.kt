package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.checkPermission
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles /zrgroup commands for region group management.
 * Supports hybrid input: arguments or chat prompts.
 */
class GroupCommand(private val plugin: ZoneRental) : CommandExecutor, TabCompleter {

    companion object {
        private val SUBCOMMANDS = listOf("create", "edit", "delete", "list", "view")
        private val EDIT_ACTIONS = listOf("add", "remove")
        const val PENDING_TIMEOUT = 60_000L
        private const val GROUP_CACHE_TTL = 5_000L
    }

    // Pending actions for chat-based input
    private val pendingActions = ConcurrentHashMap<UUID, PendingGroupAction>()

    // Tab completion cache
    private var cachedGroupNames: List<String>? = null
    private var groupCacheExpiry: Long = 0

    /**
     * Gets group names with caching to avoid repeated disk reads during tab completion.
     */
    private fun getCachedGroupNames(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedGroupNames == null || now > groupCacheExpiry) {
            cachedGroupNames = plugin.groupsConfig.allGroups.toList()
            groupCacheExpiry = now + GROUP_CACHE_TTL
        }
        return cachedGroupNames!!
    }

    /**
     * Invalidates the group name cache (call when groups are modified).
     */
    fun invalidateGroupCache() {
        cachedGroupNames = null
        groupCacheExpiry = 0
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        sender.checkPermission("zonerental.admin.group", "${ChatColor.RED}You don't have permission to use this command.")
            ?: return true

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "edit" -> handleEdit(sender, args)
            "delete" -> handleDelete(sender, args)
            "list" -> handleList(sender)
            "view" -> handleView(sender, args)
            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown subcommand: ${args[0]}")
                sendHelp(sender)
                true
            }
        }
    }

    // ========== Subcommand Handlers ==========

    private fun handleCreate(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /zrgroup create <group_name> [regions]")
            return true
        }

        val groupName = args[1]

        // Validate group name
        plugin.groupsConfig.getValidationError(groupName)?.let { error ->
            sender.sendMessage("${ChatColor.RED}$error")
            return true
        }

        // Check if group already exists
        if (plugin.groupsConfig.groupExists(groupName)) {
            sender.sendMessage("${ChatColor.RED}Group '$groupName' already exists!")
            return true
        }

        // Hybrid approach: regions provided or prompt
        if (args.size > 2) {
            val regionsInput = args.drop(2).joinToString(" ")
            processCreateGroup(sender, groupName, regionsInput)
        } else {
            promptForRegions(sender, groupName, "create")
        }

        return true
    }

    private fun handleEdit(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /zrgroup edit <group_name> <add|remove> [regions]")
            return true
        }

        val groupName = args[1]
        val action = args[2].lowercase()

        // Validate group exists
        if (!plugin.groupsConfig.groupExists(groupName)) {
            sender.sendMessage("${ChatColor.RED}Group '$groupName' does not exist!")
            return true
        }

        // Validate action
        if (action !in EDIT_ACTIONS) {
            sender.sendMessage("${ChatColor.RED}Action must be 'add' or 'remove'")
            return true
        }

        // Hybrid approach
        if (args.size > 3) {
            val regionsInput = args.drop(3).joinToString(" ")
            when (action) {
                "add" -> processAddRegions(sender, groupName, regionsInput)
                "remove" -> processRemoveRegions(sender, groupName, regionsInput)
            }
        } else {
            promptForRegions(sender, groupName, action)
        }

        return true
    }

    private fun handleDelete(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /zrgroup delete <group_name> [confirm]")
            return true
        }

        val groupName = args[1]

        // Validate group exists
        if (!plugin.groupsConfig.groupExists(groupName)) {
            sender.sendMessage("${ChatColor.RED}Group '$groupName' does not exist!")
            return true
        }

        // Require confirmation
        if (args.size < 3 || !args[2].equals("confirm", ignoreCase = true)) {
            val regionCount = plugin.groupsConfig.getGroupSize(groupName)
            sender.sendMessage("${ChatColor.YELLOW}WARNING: You are about to delete group '$groupName' with $regionCount regions!")
            sender.sendMessage("${ChatColor.YELLOW}Group overrides in regions.yml will be removed.")
            sender.sendMessage("${ChatColor.YELLOW}Individual region overrides will remain intact.")
            sender.sendMessage("${ChatColor.RED}Type: /zrgroup delete $groupName confirm")
            return true
        }

        // Get all regions in group BEFORE deletion (needed for marking signs dirty)
        val groupRegions = plugin.groupsConfig.getGroupRegions(groupName)

        // Delete group from groups.yml
        plugin.groupsConfig.deleteGroup(groupName)

        // Remove group overrides from regions.yml
        plugin.regionsConfig.removeGroupOverrides(groupName)

        // Mark all signs as dirty so they update to fallback prices
        plugin.signManager.bulkMarkSignsDirty(groupRegions)

        sender.sendMessage("${ChatColor.GREEN}Deleted group '$groupName' and removed all group overrides")

        invalidateGroupCache()

        return true
    }

    private fun handleList(sender: CommandSender): Boolean {
        val allGroups = plugin.groupsConfig.allGroups

        if (allGroups.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}No groups exist. Create one with /zrgroup create <name>")
            return true
        }

        sender.sendMessage("${ChatColor.GOLD}=== Region Groups ===")

        for (groupName in allGroups) {
            val size = plugin.groupsConfig.getGroupSize(groupName)
            sender.sendMessage("${ChatColor.GREEN}  $groupName${ChatColor.GRAY} ($size regions)")
        }

        val stats = plugin.groupsConfig.getStatistics()
        val totalGroups = stats["group_count"] as Int
        val totalRegions = stats["total_regions"] as Int

        sender.sendMessage("${ChatColor.GOLD}Total: $totalGroups groups, $totalRegions regions")
        sender.sendMessage("${ChatColor.GRAY}Use /zrgroup view <group_name> for details")

        return true
    }

    private fun handleView(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /zrgroup view <group_name>")
            return true
        }

        val groupName = args[1]

        // Validate group exists
        if (!plugin.groupsConfig.groupExists(groupName)) {
            sender.sendMessage("${ChatColor.RED}Group '$groupName' does not exist!")
            return true
        }

        val regions = plugin.groupsConfig.getGroupRegions(groupName)

        sender.sendMessage("${ChatColor.GOLD}=== Group: $groupName ===")
        sender.sendMessage("${ChatColor.YELLOW}Regions (${regions.size}):")

        for (compositeKey in regions) {
            sender.sendMessage("${ChatColor.GREEN}  - $compositeKey")
        }

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GRAY}Apply overrides: /zroverride <setting> $groupName <value>")

        return true
    }

    // ========== Processing Methods ==========

    fun processCreateGroup(sender: CommandSender, groupName: String, regionsInput: String) {
        val parseResult = parseRegionInput(sender, regionsInput)

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}No valid regions provided!")
            sendParseErrors(sender, parseResult)
            return
        }

        // Check for duplicate membership
        val duplicates = plugin.groupsConfig.checkGroupMembership(parseResult.validRegions)

        if (duplicates.isNotEmpty()) {
            sender.sendMessage("${ChatColor.RED}Some regions are already in groups:")
            for ((region, existingGroup) in duplicates) {
                sender.sendMessage("${ChatColor.YELLOW}  - $region is in group '$existingGroup'")
            }
            sender.sendMessage("${ChatColor.RED}Remove them from their groups first.")
            return
        }

        // Create group
        plugin.groupsConfig.createGroup(groupName, parseResult.validRegions)

        // Clean up individual region overrides
        val cleanedCount = parseResult.validRegions.count { compositeKey ->
            if (plugin.regionsConfig.hasRegionOverrides(compositeKey)) {
                plugin.regionsConfig.removeRegionOverrides(compositeKey)
                true
            } else false
        }

        // Mark signs as dirty
        plugin.signManager.bulkMarkSignsDirty(parseResult.validRegions)

        sender.sendMessage("${ChatColor.GREEN}Created group '$groupName' with ${parseResult.validRegions.size} regions")

        notifyCleanedOverrides(sender, cleanedCount)
        sendParseErrors(sender, parseResult)

        invalidateGroupCache()
    }

    fun processAddRegions(sender: CommandSender, groupName: String, regionsInput: String) {
        val parseResult = parseRegionInput(sender, regionsInput)

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}No valid regions provided!")
            return
        }

        // Check for duplicate membership
        val duplicates = plugin.groupsConfig.checkGroupMembership(parseResult.validRegions)

        if (duplicates.isNotEmpty()) {
            sender.sendMessage("${ChatColor.RED}Some regions are already in groups:")
            for ((region, existingGroup) in duplicates) {
                val message = if (existingGroup == groupName) {
                    "${ChatColor.YELLOW}  - $region is already in this group"
                } else {
                    "${ChatColor.YELLOW}  - $region is in group '$existingGroup'"
                }
                sender.sendMessage(message)
            }
            sender.sendMessage("${ChatColor.RED}Remove them from other groups first.")
            return
        }

        // Add regions
        plugin.groupsConfig.addRegionsToGroup(groupName, parseResult.validRegions)

        // Clean up individual region overrides
        val cleanedCount = parseResult.validRegions.count { compositeKey ->
            if (plugin.regionsConfig.hasRegionOverrides(compositeKey)) {
                plugin.regionsConfig.removeRegionOverrides(compositeKey)
                true
            } else false
        }

        // Mark signs as dirty
        plugin.signManager.bulkMarkSignsDirty(parseResult.validRegions)

        val totalSize = plugin.groupsConfig.getGroupSize(groupName)
        sender.sendMessage("${ChatColor.GREEN}Added ${parseResult.validRegions.size} regions to group '$groupName' (Total: $totalSize regions)")

        notifyCleanedOverrides(sender, cleanedCount)
        sendParseErrors(sender, parseResult)

        invalidateGroupCache()
    }

    fun processRemoveRegions(sender: CommandSender, groupName: String, regionsInput: String) {
        val parseResult = parseRegionInput(sender, regionsInput)

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}No valid regions provided!")
            return
        }

        // Get current regions in group
        val currentRegions = plugin.groupsConfig.getGroupRegions(groupName)

        // Partition into regions to remove and those not in group
        val (toRemove, notInGroup) = parseResult.validRegions.partition { it in currentRegions }

        if (toRemove.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}None of the specified regions are in group '$groupName'")
            return
        }

        // Remove regions
        plugin.groupsConfig.removeRegionsFromGroup(groupName, toRemove)

        // Mark signs as dirty
        plugin.signManager.bulkMarkSignsDirty(toRemove)

        val remainingSize = plugin.groupsConfig.getGroupSize(groupName)
        sender.sendMessage("${ChatColor.GREEN}Removed ${toRemove.size} regions from group '$groupName' (Remaining: $remainingSize regions)")

        if (notInGroup.isNotEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Note: ${notInGroup.size} regions were not in the group")
        }

        invalidateGroupCache()
    }

    // ========== Helper Methods ==========

    private fun parseRegionInput(sender: CommandSender, input: String): ParseResult {
        val validRegions = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val defaultWorld = (sender as? Player)?.world

        input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                val result = parseRegionPart(part, defaultWorld)
                when {
                    result.first != null -> validRegions.add(result.first!!)
                    result.second != null -> errors.add(result.second!!)
                }
            }

        return ParseResult(validRegions, errors)
    }

    private fun parseRegionPart(part: String, defaultWorld: World?): Pair<String?, String?> {
        return if (":" in part) {
            parseExplicitWorldRegion(part)
        } else {
            parseImplicitWorldRegion(part, defaultWorld)
        }
    }

    private fun parseExplicitWorldRegion(part: String): Pair<String?, String?> {
        val (worldName, regionName) = part.split(":", limit = 2)

        val world = Bukkit.getWorld(worldName)
            ?: return null to "World '$worldName' not found"

        val compositeKey = "$worldName:$regionName"

        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            return null to "Region '$compositeKey' not found in WorldGuard"
        }

        return compositeKey to null
    }

    private fun parseImplicitWorldRegion(part: String, defaultWorld: World?): Pair<String?, String?> {
        if (defaultWorld == null) {
            return null to "Console must use world:region format (e.g., world:$part)"
        }

        val compositeKey = "${defaultWorld.name}:$part"

        if (!plugin.worldGuardManager.regionExists(part, defaultWorld)) {
            return null to "Region '$compositeKey' not found in WorldGuard"
        }

        return compositeKey to null
    }

    private fun sendParseErrors(sender: CommandSender, result: ParseResult) {
        if (result.errors.isNotEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Warnings (${result.errors.size} regions skipped):")
            result.errors.forEach { sender.sendMessage("${ChatColor.YELLOW}  - $it") }
        }
    }

    private fun notifyCleanedOverrides(sender: CommandSender, count: Int) {
        if (count > 0) {
            sender.sendMessage("${ChatColor.YELLOW}Removed individual overrides from $count region(s) to prevent conflicts with group overrides")
        }
    }

    private fun promptForRegions(sender: CommandSender, groupName: String, action: String) {
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}Console must provide regions as arguments.")
            val usage = when (action) {
                "create" -> "Usage: /zrgroup create $groupName region1,region2,world:region3"
                "add" -> "Usage: /zrgroup edit $groupName add region1,region2"
                "remove" -> "Usage: /zrgroup edit $groupName remove region1,region2"
                else -> ""
            }
            sender.sendMessage("${ChatColor.GRAY}$usage")
            return
        }

        // Create pending action
        pendingActions[player.uniqueId] = PendingGroupAction(groupName, action)

        sender.sendMessage("${ChatColor.GREEN}Please type the regions in chat:")
        sender.sendMessage("${ChatColor.GRAY}Format: region1,region2 or world:region1,region2")
        sender.sendMessage("${ChatColor.GRAY}Use world:region for regions in different worlds")
        sender.sendMessage("${ChatColor.YELLOW}Type 'cancel' to abort (60 second timeout)")
    }

    // ========== Pending Actions ==========

    fun hasPendingAction(playerUUID: UUID): Boolean = pendingActions.containsKey(playerUUID)

    fun getPendingAction(playerUUID: UUID): PendingGroupAction? = pendingActions[playerUUID]

    fun cancelPendingAction(playerUUID: UUID) {
        pendingActions.remove(playerUUID)
    }

    fun handleChatInput(player: Player, message: String) {
        val action = pendingActions.remove(player.uniqueId) ?: return

        when (action.action) {
            "create" -> processCreateGroup(player, action.groupName, message)
            "add" -> processAddRegions(player, action.groupName, message)
            "remove" -> processRemoveRegions(player, action.groupName, message)
        }
    }

    fun cleanupExpiredActions() {
        val now = System.currentTimeMillis()
        val expired = pendingActions.entries
            .filter { now - it.value.timestamp > PENDING_TIMEOUT }
            .map { it.key }

        expired.forEach { uuid ->
            pendingActions.remove(uuid)
            Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }?.sendMessage(
                "${ChatColor.YELLOW}Region input timed out. Please run the command again."
            )
        }
    }

    // ========== Tab Completion ==========

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("zonerental.admin.group")) return emptyList()

        val completions = when (args.size) {
            1 -> SUBCOMMANDS
            2 -> getSecondArgCompletions(args[0])
            3 -> getThirdArgCompletions(args[0])
            else -> getRegionSuggestions(sender, args.last())
        }

        val currentArg = args.last().lowercase()
        return completions.filter { it.lowercase().startsWith(currentArg) }
    }

    private fun getSecondArgCompletions(subcommand: String): List<String> = when (subcommand.lowercase()) {
        "edit", "delete", "view" -> getCachedGroupNames()
        else -> emptyList()
    }

    private fun getThirdArgCompletions(subcommand: String): List<String> = when (subcommand.lowercase()) {
        "edit" -> EDIT_ACTIONS
        "delete" -> listOf("confirm")
        "create" -> emptyList() // Region suggestions handled in else branch
        else -> emptyList()
    }

    private fun getRegionSuggestions(sender: CommandSender, partial: String): List<String> {
        val suggestions = mutableListOf<String>()

        if (":" in partial) {
            val parts = partial.split(":", limit = 2)
            val worldName = parts[0]
            val regionPrefix = parts.getOrElse(1) { "" }

            val world = Bukkit.getWorld(worldName)
            if (world != null) {
                // Suggest regions from that world with world: prefix
                plugin.worldGuardManager.allRegionNames
                    .filter { it.lowercase().startsWith(regionPrefix.lowercase()) }
                    .mapTo(suggestions) { "$worldName:$it" }
            } else {
                // World not found, suggest world names
                Bukkit.getWorlds()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(worldName.lowercase()) }
                    .mapTo(suggestions) { "$it:" }
            }
        } else {
            // No colon - suggest regions from player's world and world prefixes
            if (sender is Player) {
                val worldName = sender.world.name

                // Suggest regions from player's current world
                plugin.worldGuardManager.allRegionNames
                    .filter { it.lowercase().startsWith(partial.lowercase()) }
                    .forEach { suggestions.add(it) }

                // Also suggest world: prefix for current world
                if ("$worldName:".lowercase().startsWith(partial.lowercase())) {
                    suggestions.add("$worldName:")
                }
            }

            // Suggest all world names with : suffix
            Bukkit.getWorlds()
                .map { "${it.name}:" }
                .filter { it.lowercase().startsWith(partial.lowercase()) }
                .forEach { suggestions.add(it) }
        }

        return suggestions
    }

    // ========== Help ==========

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}=== ZoneRental Groups ===")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup create <name> [regions]${ChatColor.GRAY} - Create group")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup edit <name> add [regions]${ChatColor.GRAY} - Add regions")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup edit <name> remove [regions]${ChatColor.GRAY} - Remove regions")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup delete <name>${ChatColor.GRAY} - Delete group")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup list${ChatColor.GRAY} - List all groups")
        sender.sendMessage("${ChatColor.YELLOW}/zrgroup view <name>${ChatColor.GRAY} - View group details")
        sender.sendMessage("${ChatColor.GRAY}Regions format: region1,region2,world:region3")
    }
}

// ========== Data Classes ==========

data class PendingGroupAction(
    val groupName: String,
    val action: String,  // "create", "add", "remove"
    val timestamp: Long = System.currentTimeMillis()
)

internal data class ParseResult(
    val validRegions: List<String>,
    val errors: List<String>
)
