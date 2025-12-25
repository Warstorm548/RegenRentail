package com.regionrental.commands

import com.regionrental.RegionRental
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to list a player's active rentals.
 * Usage: /rrlist [player]
 */
class ListCommand(private val plugin: RegionRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("regionrental.list")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        // Determine target player
        val (targetUUID, targetName) = when {
            args.isNotEmpty() -> {
                // Viewing another player's rentals - requires admin permission
                if (!sender.hasPermission("regionrental.admin.list.others")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to view other players' rentals!")
                    return true
                }

                @Suppress("DEPRECATION")
                val targetPlayer = Bukkit.getOfflinePlayer(args[0])

                if (!targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage("${ChatColor.RED}Player ${args[0]} not found!")
                    return true
                }

                targetPlayer.uniqueId to "${targetPlayer.name}'s"
            }
            sender is Player -> {
                // Viewing own rentals
                sender.uniqueId to "Your"
            }
            else -> {
                // Console without player argument
                sender.sendMessage("${ChatColor.RED}Console must specify a player: /rrlist <player>")
                return true
            }
        }

        val rentals = plugin.rentalManager.getPlayerRentals(targetUUID)

        if (rentals.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}$targetName active rentals: ${ChatColor.WHITE}None")
            return true
        }

        sender.sendMessage("${ChatColor.GOLD}=== $targetName Active Rentals ===")

        for (rental in rentals) {
            val status = if (rental.isExpired) "${ChatColor.RED}EXPIRED" else "${ChatColor.GREEN}ACTIVE"
            sender.sendMessage(
                "${ChatColor.YELLOW}• ${rental.regionName} - $status${ChatColor.WHITE}" +
                " - Expires: ${rental.formattedEndDate} (${rental.daysRemaining} days remaining)"
            )
        }

        sender.sendMessage(
            "${ChatColor.GRAY}Total rentals: ${rentals.size}/${plugin.configManager.maxRentalsPerPlayer}"
        )

        return true
    }
}
