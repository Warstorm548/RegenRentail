package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.sendMiniMessage
import com.zonerental.util.WorldRegionParser
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command to view refund history for a rental region.
 * Shows all refund transactions with timestamps, amounts, and reasons.
 */
class RefundHistoryCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.admin.refundhistory")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMiniMessage("<red>Usage: /zrrefundhistory <world:region>")
            sender.sendMiniMessage("<yellow>Example: /zrrefundhistory world:shop1")
            sender.sendMiniMessage("<yellow>Shows refund transaction history for a rental region.")
            return true
        }

        // Parse region argument with world inference
        val parsed = WorldRegionParser.parse(args[0], sender) ?: run {
            sender.sendMiniMessage("<red>Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld() ?: run {
            sender.sendMiniMessage("<red>World '${parsed.worldName}' is not loaded!")
            return true
        }
        val regionName = parsed.regionName

        // Get rental
        val rental = plugin.rentalManager.getRental(regionName, world) ?: run {
            sender.sendMiniMessage("<red>Region ${parsed.getCompositeKey()} is not currently rented!")
            sender.sendMiniMessage("<yellow>Refund history is only available for active rentals.")
            return true
        }

        // Get refund data
        val refundHistory = rental.getRefundHistory()
        val currencyFormat = plugin.configManager.currencyFormat
        val formattedTotal = String.format(currencyFormat, rental.totalPaid)
        val formattedRefunded = String.format(currencyFormat, rental.totalRefunded)
        val formattedNet = String.format(currencyFormat, rental.netRefundableAmount)

        // Build header
        val divider = "<gold><bold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        sender.sendMiniMessage("")
        sender.sendMiniMessage(divider)
        sender.sendMessage(plugin.configManager.getMessage("refund-history-header", "{region}", parsed.getCompositeKey()))
        sender.sendMiniMessage(divider)
        sender.sendMiniMessage("")

        // Rental summary
        sender.sendMiniMessage("<yellow><bold>Rental Summary:")
        sender.sendMiniMessage("<gray>  Player: <white>${rental.playerName}")
        sender.sendMiniMessage("<gray>  Total Paid: <green>$formattedTotal")
        sender.sendMiniMessage("<gray>  Total Refunded: <yellow>$formattedRefunded")
        sender.sendMiniMessage("<gray>  Net Amount: <aqua>$formattedNet")
        sender.sendMiniMessage("")

        // Refund transactions
        if (refundHistory.isEmpty()) {
            sender.sendMiniMessage("<gray>No refund transactions yet.")
        } else {
            sender.sendMiniMessage("<yellow><bold>Refund Transactions:")
            sender.sendMiniMessage("")

            refundHistory.forEachIndexed { index, record ->
                val formattedAmount = String.format(currencyFormat, record.amount)
                val reason = formatReason(record.reason)
                sender.sendMiniMessage(
                    "<gray>  ${index + 1}. " +
                    "<green>$formattedAmount<gray> - " +
                    "<white>${record.formattedTimestamp}<gray> - " +
                    "<yellow>$reason" +
                    "<gray> (by <aqua>${record.adminName}<gray>)"
                )
            }

            sender.sendMiniMessage("")
            sender.sendMiniMessage("<gray>Total Refunds: <white>${refundHistory.size}")
        }

        sender.sendMiniMessage(divider)
        sender.sendMiniMessage("")

        return true
    }

    /**
     * Format refund reason for display.
     */
    private fun formatReason(reason: String): String = when (reason) {
        "duration_reset" -> "Duration Reset"
        "time_removal" -> "Time Removal"
        "admin_reset" -> "Admin Reset"
        else -> reason
    }
}
