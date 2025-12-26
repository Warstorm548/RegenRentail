package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.util.WorldRegionParser
import org.bukkit.ChatColor
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
            sender.sendMessage("${ChatColor.RED}Usage: /zrinfo <world:region>")
            sender.sendMessage("${ChatColor.YELLOW}Example: /zrinfo world:shop1")
            return true
        }

        // Parse region argument with world inference
        val parsed = WorldRegionParser.parse(args[0], sender) ?: run {
            sender.sendMessage("${ChatColor.RED}Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld() ?: run {
            sender.sendMessage("${ChatColor.RED}World not found!")
            return true
        }
        val regionName = parsed.regionName

        // Check if region exists
        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            sender.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", parsed.getCompositeKey()))
            return true
        }

        val rental = plugin.rentalManager.getRental(regionName, world)

        sender.sendMessage("${ChatColor.GOLD}=== Region: ${parsed.getCompositeKey()} ===")

        if (rental == null) {
            // Region is available
            val price = String.format(plugin.configManager.currencyFormat,
                plugin.configManager.getPriceForRegion(regionName, world))
            val duration = plugin.configManager.getDurationForRegion(regionName, world)

            sender.sendMessage("${ChatColor.GREEN}Status: ${ChatColor.WHITE}Available")
            sender.sendMessage("${ChatColor.YELLOW}Price: ${ChatColor.WHITE}$price")
            sender.sendMessage("${ChatColor.YELLOW}Duration: ${ChatColor.WHITE}$duration days")
        } else {
            // Region is rented
            val hoursRemaining = rental.hoursRemaining % 24
            val totalPaid = String.format(plugin.configManager.currencyFormat, rental.totalPaid)

            sender.sendMessage("${ChatColor.RED}Status: ${ChatColor.WHITE}Rented")
            sender.sendMessage("${ChatColor.YELLOW}Owner: ${ChatColor.WHITE}${rental.playerName}")
            sender.sendMessage("${ChatColor.YELLOW}Expires: ${ChatColor.WHITE}${rental.formattedEndDate}")
            sender.sendMessage("${ChatColor.YELLOW}Time Remaining: ${ChatColor.WHITE}${rental.daysRemaining} days, $hoursRemaining hours")
            sender.sendMessage("${ChatColor.YELLOW}Extensions Used: ${ChatColor.WHITE}${rental.extensionCount}/${plugin.configManager.maxExtensions}")
            sender.sendMessage("${ChatColor.YELLOW}Total Paid: ${ChatColor.WHITE}$totalPaid")
        }

        return true
    }
}
