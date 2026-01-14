package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.sendMiniMessage
import com.zonerental.util.WorldRegionParser
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class ResetCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.admin.reset")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMiniMessage("<red>Usage: /zrreset <world:region>")
            sender.sendMiniMessage("<yellow>Example: /zrreset world:shop1")
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

        val rental = plugin.rentalManager.getRental(regionName, world)
        if (rental == null) {
            sender.sendMiniMessage("<red>Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        // Reset the rental with net refund (prevents double-refunds)
        val refundDetails = plugin.rentalManager.resetRentalWithRefund(regionName, world)

        if (refundDetails != null) {
            val playerName = refundDetails["playerName"] as String
            val refundAmount = refundDetails["refundAmount"] as Double
            val totalPaid = refundDetails["totalPaid"] as Double
            val alreadyRefunded = refundDetails["alreadyRefunded"] as Double

            val formattedAmount = String.format(plugin.configManager.currencyFormat, refundAmount)
            val formattedTotal = String.format(plugin.configManager.currencyFormat, totalPaid)
            val formattedAlready = String.format(plugin.configManager.currencyFormat, alreadyRefunded)

            // Send success message to admin with detailed refund information
            sender.sendMessage(plugin.configManager.getMessage("admin-reset-success",
                "{region}", regionName,
                "{player}", playerName,
                "{amount}", formattedAmount))

            // Show refund breakdown if any previous refunds exist
            if (alreadyRefunded > 0) {
                sender.sendMiniMessage("<gray>  Total paid: <yellow>$formattedTotal")
                sender.sendMiniMessage("<gray>  Already refunded: <yellow>$formattedAlready")
                sender.sendMiniMessage("<gray>  Net refund: <green>$formattedAmount")
            }

            // Log the action
            val refundLog = if (alreadyRefunded > 0) " (Total paid: $formattedTotal, Already refunded: $formattedAlready)" else ""
            plugin.logger.info("Admin ${sender.name} reset rental for region $regionName. Refunded $formattedAmount to $playerName$refundLog")
        } else {
            sender.sendMiniMessage("<red>Failed to reset rental for region $regionName")
        }

        return true
    }
}
