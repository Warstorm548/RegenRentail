package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.sendMiniMessage
import com.zonerental.util.WorldRegionParser
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class InfoCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.info")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMiniMessage("<red>Usage: /zrinfo <world:region>")
            sender.sendMiniMessage("<yellow>Example: /zrinfo world:shop1")
            return true
        }

        // Parse region argument with world inference
        val parsed = WorldRegionParser.parse(args[0], sender) ?: run {
            sender.sendMiniMessage("<red>Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld() ?: run {
            sender.sendMiniMessage("<red>World not found!")
            return true
        }
        val regionName = parsed.regionName

        // Check if region exists
        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            sender.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", parsed.getCompositeKey()))
            return true
        }

        val rental = plugin.rentalManager.getRental(regionName, world)

        sender.sendMiniMessage("<gold>=== Region: ${parsed.getCompositeKey()} ===")

        if (rental == null) {
            // Region is available
            val price = String.format(plugin.configManager.currencyFormat,
                plugin.configManager.getPriceForRegion(regionName, world))
            val duration = plugin.configManager.getDurationForRegion(regionName, world)

            sender.sendMiniMessage("<green>Status: <white>Available")
            sender.sendMiniMessage("<yellow>Price: <white>$price")
            sender.sendMiniMessage("<yellow>Duration: <white>$duration days")
        } else {
            // Region is rented
            val hoursRemaining = rental.hoursRemaining % 24
            val totalPaid = String.format(plugin.configManager.currencyFormat, rental.totalPaid)

            sender.sendMiniMessage("<red>Status: <white>Rented")
            sender.sendMiniMessage("<yellow>Owner: <white>${rental.playerName}")
            sender.sendMiniMessage("<yellow>Expires: <white>${rental.formattedEndDate}")
            sender.sendMiniMessage("<yellow>Time Remaining: <white>${rental.daysRemaining} days, $hoursRemaining hours")
            sender.sendMiniMessage("<yellow>Extensions Used: <white>${rental.extensionCount}/${plugin.configManager.maxExtensions}")
            sender.sendMiniMessage("<yellow>Total Paid: <white>$totalPaid")
        }

        return true
    }
}
