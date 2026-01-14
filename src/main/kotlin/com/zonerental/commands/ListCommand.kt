package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.sendMiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to list a player's active rentals.
 * Usage: /zrlist [player]
 */
class ListCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.list")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        // Determine target player
        val (targetUUID, targetName) = when {
            args.isNotEmpty() -> {
                // Viewing another player's rentals - requires admin permission
                if (!sender.hasPermission("zonerental.admin.list.others")) {
                    sender.sendMiniMessage("<red>You don't have permission to view other players' rentals!")
                    return true
                }

                @Suppress("DEPRECATION")
                val targetPlayer = Bukkit.getOfflinePlayer(args[0])

                if (!targetPlayer.hasPlayedBefore()) {
                    sender.sendMiniMessage("<red>Player ${args[0]} not found!")
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
                sender.sendMiniMessage("<red>Console must specify a player: /zrlist <player>")
                return true
            }
        }

        val rentals = plugin.rentalManager.getPlayerRentals(targetUUID)

        if (rentals.isEmpty()) {
            sender.sendMiniMessage("<yellow>$targetName active rentals: <white>None")
            return true
        }

        sender.sendMiniMessage("<gold>=== $targetName Active Rentals ===")

        for (rental in rentals) {
            val status = if (rental.isExpired) "<red>EXPIRED" else "<green>ACTIVE"
            sender.sendMiniMessage(
                "<yellow>• ${rental.regionName} - $status<white>" +
                " - Expires: ${rental.formattedEndDate} (${rental.daysRemaining} days remaining)"
            )
        }

        sender.sendMiniMessage(
            "<gray>Total rentals: ${rentals.size}/${plugin.configManager.maxRentalsPerPlayer}"
        )

        return true
    }
}
