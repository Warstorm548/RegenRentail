package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.util.WorldRegionParser
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command to completely remove ZoneRental setup from a region
 * This removes signs, schematics, and configuration data
 * Useful when a region needs to be repurposed or is no longer needed for rentals
 */
class RemoveCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.admin.remove")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}Usage: /zrremove <world:region>")
            sender.sendMessage("${ChatColor.YELLOW}Example: /zrremove world:shop1")
            sender.sendMessage("${ChatColor.YELLOW}This will completely remove ZoneRental setup from the region.")
            sender.sendMessage("${ChatColor.YELLOW}If the region is currently rented, the rental will be reset with full refund.")
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

        // Check if WorldGuard region exists
        if (!plugin.worldGuardManager.regionExists(regionName, world)) {
            sender.sendMessage(plugin.configManager.getMessage("region-not-found",
                "{region}", parsed.compositeKey))
            return true
        }

        // Check if region has an active rental
        val rental = plugin.rentalManager.getRental(regionName, world)
        if (rental != null) {
            // Reset the rental with net refund first (prevents double-refunds)
            val refundDetails = plugin.rentalManager.resetRentalWithRefund(regionName, world)

            if (refundDetails != null) {
                val playerName = refundDetails["playerName"] as String
                val refundAmount = refundDetails["refundAmount"] as Double
                val totalPaid = refundDetails["totalPaid"] as Double
                val alreadyRefunded = refundDetails["alreadyRefunded"] as Double

                val formattedAmount = String.format(plugin.configManager.currencyFormat, refundAmount)
                val formattedTotal = String.format(plugin.configManager.currencyFormat, totalPaid)
                val formattedAlready = String.format(plugin.configManager.currencyFormat, alreadyRefunded)

                sender.sendMessage("${ChatColor.YELLOW}Active rental found. Player $playerName has been refunded $formattedAmount")

                // Show refund breakdown if any previous refunds exist
                if (alreadyRefunded > 0) {
                    sender.sendMessage("${ChatColor.GRAY}  Total paid: ${ChatColor.YELLOW}$formattedTotal")
                    sender.sendMessage("${ChatColor.GRAY}  Already refunded: ${ChatColor.YELLOW}$formattedAlready")
                    sender.sendMessage("${ChatColor.GRAY}  Net refund: ${ChatColor.GREEN}$formattedAmount")
                }
            }
        }

        // Remove rental sign
        val signRemoved = plugin.signManager.removeRegionSetup(regionName, world)

        // Delete WorldEdit schematic if it exists
        val schematicDeleted = if (plugin.worldEditManager.hasCapture(regionName, world)) {
            plugin.worldEditManager.deleteCapture(regionName, world)
            true
        } else {
            false
        }

        // Remove region from regions.yml
        val regionConfigRemoved = if (plugin.regionsConfig.hasRegion(regionName, world)) {
            plugin.regionsConfig.removeRegion(regionName, world)
            true
        } else {
            false
        }

        // Send comprehensive success message
        val message = buildString {
            append(plugin.configManager.getMessage("region-removed", "{region}", parsed.compositeKey))

            if (signRemoved) {
                append("\n${ChatColor.GREEN}  ✓ Rental sign removed")
            }

            if (schematicDeleted) {
                append("\n${ChatColor.GREEN}  ✓ WorldEdit schematic deleted")
            }

            if (regionConfigRemoved) {
                append("\n${ChatColor.GREEN}  ✓ Region configuration removed from regions.yml")
            }

            if (rental != null) {
                append("\n${ChatColor.GREEN}  ✓ Active rental reset with refund")
            }
        }

        sender.sendMessage(message)

        // Log the action
        plugin.logger.info("Admin ${sender.name} removed RegionRental setup from region: ${parsed.compositeKey}")

        return true
    }
}
