package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.util.WorldRegionParser
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Unified member management command with subcommands: add, remove, list.
 * Merged from MemberCommand.java and MembersCommand.java.
 */
class MemberCommand(private val plugin: ZoneRental) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "add" -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "list" -> handleList(sender, args)
            else -> {
                sendUsage(sender)
                true
            }
        }
    }

    private fun sendUsage(player: Player) {
        player.sendMessage("${ChatColor.RED}Usage: /zrmember <add|remove|list> ...")
        player.sendMessage("${ChatColor.YELLOW}  add <region> <username>    - Add a member to your rental")
        player.sendMessage("${ChatColor.YELLOW}  remove <region> <username> - Remove a member from your rental")
        player.sendMessage("${ChatColor.YELLOW}  list <region>              - View members of a rental")
    }

    private fun handleAdd(player: Player, args: Array<String>): Boolean {
        if (!player.hasPermission("zonerental.member")) {
            player.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (!plugin.configManager.isMemberManagementEnabled) {
            player.sendMessage(plugin.configManager.getMessage("member-management-disabled"))
            return true
        }

        if (args.size < 3) {
            player.sendMessage("${ChatColor.RED}Usage: /zrmember add <region> <username>")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember add shop1 PlayerName")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember add world:shop1 PlayerName")
            return true
        }

        val parsed = WorldRegionParser.parse(args[1], player) ?: run {
            player.sendMessage("${ChatColor.RED}Invalid region format!")
            return true
        }

        val world = parsed.getWorld() ?: run {
            player.sendMessage("${ChatColor.RED}World not found!")
            return true
        }
        val regionName = parsed.regionName
        val memberName = args[2]

        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            player.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", parsed.getCompositeKey()))
            return true
        }

        val rental = plugin.rentalManager.getRental(regionName, world)
        if (rental == null) {
            player.sendMessage("${ChatColor.RED}Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        if (rental.playerUUID != player.uniqueId) {
            player.sendMessage(plugin.configManager.getMessage("not-rental-owner"))
            return true
        }

        val memberPlayer = Bukkit.getOfflinePlayer(memberName)
        val memberUUID = memberPlayer.uniqueId

        if (memberUUID == player.uniqueId) {
            player.sendMessage(plugin.configManager.getMessage("cannot-add-self"))
            return true
        }

        if (rental.hasMember(memberUUID)) {
            player.sendMessage(plugin.configManager.getMessage("member-already-added", "{member}", memberPlayer.name ?: memberUUID.toString()))
            return true
        }

        val maxMembers = plugin.configManager.maxMembers
        if (maxMembers != -1 && rental.memberCount >= maxMembers) {
            player.sendMessage(plugin.configManager.getMessage("member-limit-reached",
                "{current}", rental.memberCount.toString(),
                "{max}", maxMembers.toString()))
            return true
        }

        if (plugin.rentalManager.addMemberToRental(regionName, world, player.uniqueId, memberUUID)) {
            player.sendMessage(plugin.configManager.getMessage("member-added",
                "{member}", memberPlayer.name ?: memberUUID.toString(),
                "{region}", parsed.getCompositeKey()))

            memberPlayer.player?.sendMessage(plugin.configManager.getMessage("member-added-notify",
                "{region}", parsed.getCompositeKey(),
                "{owner}", player.name))
        } else {
            player.sendMessage("${ChatColor.RED}Failed to add member!")
        }

        return true
    }

    private fun handleRemove(player: Player, args: Array<String>): Boolean {
        if (!player.hasPermission("zonerental.member")) {
            player.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (!plugin.configManager.isMemberManagementEnabled) {
            player.sendMessage(plugin.configManager.getMessage("member-management-disabled"))
            return true
        }

        if (args.size < 3) {
            player.sendMessage("${ChatColor.RED}Usage: /zrmember remove <region> <username>")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember remove shop1 PlayerName")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember remove world:shop1 PlayerName")
            return true
        }

        val parsed = WorldRegionParser.parse(args[1], player) ?: run {
            player.sendMessage("${ChatColor.RED}Invalid region format!")
            return true
        }

        val world = parsed.getWorld() ?: run {
            player.sendMessage("${ChatColor.RED}World not found!")
            return true
        }
        val regionName = parsed.regionName
        val memberName = args[2]

        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            player.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", parsed.getCompositeKey()))
            return true
        }

        val rental = plugin.rentalManager.getRental(regionName, world)
        if (rental == null) {
            player.sendMessage("${ChatColor.RED}Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        if (rental.playerUUID != player.uniqueId) {
            player.sendMessage(plugin.configManager.getMessage("not-rental-owner"))
            return true
        }

        val memberPlayer = Bukkit.getOfflinePlayer(memberName)
        val memberUUID = memberPlayer.uniqueId

        if (!rental.hasMember(memberUUID)) {
            player.sendMessage(plugin.configManager.getMessage("member-not-found", "{member}", memberPlayer.name ?: memberUUID.toString()))
            return true
        }

        if (plugin.rentalManager.removeMemberFromRental(regionName, world, player.uniqueId, memberUUID)) {
            player.sendMessage(plugin.configManager.getMessage("member-removed",
                "{member}", memberPlayer.name ?: memberUUID.toString(),
                "{region}", parsed.getCompositeKey()))

            memberPlayer.player?.sendMessage(plugin.configManager.getMessage("member-removed-notify",
                "{region}", parsed.getCompositeKey(),
                "{owner}", player.name))
        } else {
            player.sendMessage("${ChatColor.RED}Failed to remove member!")
        }

        return true
    }

    private fun handleList(player: Player, args: Array<String>): Boolean {
        if (!player.hasPermission("zonerental.members")) {
            player.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /zrmember list <region>")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember list shop1")
            player.sendMessage("${ChatColor.YELLOW}Example: /zrmember list world:shop1")
            return true
        }

        val parsed = WorldRegionParser.parse(args[1], player) ?: run {
            player.sendMessage("${ChatColor.RED}Invalid region format!")
            return true
        }

        val world = parsed.getWorld() ?: run {
            player.sendMessage("${ChatColor.RED}World not found!")
            return true
        }
        val regionName = parsed.regionName

        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            player.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", parsed.getCompositeKey()))
            return true
        }

        val rental = plugin.rentalManager.getRental(regionName, world)
        if (rental == null) {
            player.sendMessage("${ChatColor.RED}Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        player.sendMessage(plugin.configManager.getMessage("members-list-header", "{region}", parsed.getCompositeKey()))

        val members = rental.getMembers()
        if (members.isEmpty()) {
            player.sendMessage(plugin.configManager.getMessage("members-list-empty"))
        } else {
            for (memberUUID in members) {
                val memberPlayer = Bukkit.getOfflinePlayer(memberUUID)
                val memberName = memberPlayer.name ?: memberUUID.toString()

                val status = if (memberPlayer.isOnline) {
                    "${ChatColor.GREEN} (Online)"
                } else {
                    "${ChatColor.GRAY} (Offline)"
                }

                player.sendMessage(plugin.configManager.getMessage("members-list-entry", "{member}", memberName + status))
            }
        }

        val maxMembers = plugin.configManager.maxMembers
        val maxDisplay = if (maxMembers == -1) "\u221E" else maxMembers.toString()

        player.sendMessage(plugin.configManager.getMessage("members-list-footer",
            "{count}", rental.memberCount.toString(),
            "{max}", maxDisplay))

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (sender !is Player) return emptyList()

        return when (args.size) {
            1 -> listOf("add", "remove", "list").filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                when (args[0].lowercase()) {
                    "add", "remove" -> {
                        // Complete region names from player's rentals
                        plugin.rentalManager.getPlayerRentals(sender.uniqueId)
                            .flatMap { listOf(it.regionName, it.compositeKey) }
                            .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                    "list" -> {
                        // Complete region names from all rentals (player can view any rental's members)
                        val completions = mutableListOf<String>()
                        for (rental in plugin.rentalManager.allRentals) {
                            if (rental.worldName == sender.world.name) {
                                completions.add(rental.regionName)
                            }
                            completions.add(rental.compositeKey)
                        }
                        completions.filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "add", "remove" -> {
                        // Complete online player names
                        Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
