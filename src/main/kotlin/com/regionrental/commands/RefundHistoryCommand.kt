package com.regionrental.commands

import com.regionrental.RegionRental
import com.regionrental.util.WorldRegionParser
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command to view refund history for a rental region.
 * Shows all refund transactions with timestamps, amounts, and reasons.
 */
class RefundHistoryCommand(private val plugin: RegionRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("regionrental.admin.refundhistory")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}Usage: /rrrefundhistory <world:region>")
            sender.sendMessage("${ChatColor.YELLOW}Example: /rrrefundhistory world:shop1")
            sender.sendMessage("${ChatColor.YELLOW}Shows refund transaction history for a rental region.")
            return true
        }

        // Parse region argument with world inference
        val parsed = WorldRegionParser.parse(args[0], sender) ?: run {
            sender.sendMessage("${ChatColor.RED}Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld()
        val regionName = parsed.regionName

        // Get rental
        val rental = plugin.rentalManager.getRental(regionName, world) ?: run {
            sender.sendMessage("${ChatColor.RED}Region ${parsed.getCompositeKey()} is not currently rented!")
            sender.sendMessage("${ChatColor.YELLOW}Refund history is only available for active rentals.")
            return true
        }

        // Get refund data
        val refundHistory = rental.getRefundHistory()
        val currencyFormat = plugin.configManager.currencyFormat
        val formattedTotal = String.format(currencyFormat, rental.totalPaid)
        val formattedRefunded = String.format(currencyFormat, rental.totalRefunded)
        val formattedNet = String.format(currencyFormat, rental.netRefundableAmount)

        // Build header
        val divider = "${ChatColor.GOLD}${ChatColor.BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        sender.sendMessage("")
        sender.sendMessage(divider)
        sender.sendMessage(plugin.configManager.getMessage("refund-history-header", "{region}", parsed.getCompositeKey()))
        sender.sendMessage(divider)
        sender.sendMessage("")

        // Rental summary
        sender.sendMessage("${ChatColor.YELLOW}${ChatColor.BOLD}Rental Summary:")
        sender.sendMessage("${ChatColor.GRAY}  Player: ${ChatColor.WHITE}${rental.playerName}")
        sender.sendMessage("${ChatColor.GRAY}  Total Paid: ${ChatColor.GREEN}$formattedTotal")
        sender.sendMessage("${ChatColor.GRAY}  Total Refunded: ${ChatColor.YELLOW}$formattedRefunded")
        sender.sendMessage("${ChatColor.GRAY}  Net Amount: ${ChatColor.AQUA}$formattedNet")
        sender.sendMessage("")

        // Refund transactions
        if (refundHistory.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}No refund transactions yet.")
        } else {
            sender.sendMessage("${ChatColor.YELLOW}${ChatColor.BOLD}Refund Transactions:")
            sender.sendMessage("")

            refundHistory.forEachIndexed { index, record ->
                val formattedAmount = String.format(currencyFormat, record.amount)
                val reason = formatReason(record.reason)
                sender.sendMessage(
                    "${ChatColor.GRAY}  ${index + 1}. " +
                    "${ChatColor.GREEN}$formattedAmount${ChatColor.GRAY} - " +
                    "${ChatColor.WHITE}${record.formattedTimestamp}${ChatColor.GRAY} - " +
                    "${ChatColor.YELLOW}$reason" +
                    "${ChatColor.GRAY} (by ${ChatColor.AQUA}${record.adminName}${ChatColor.GRAY})"
                )
            }

            sender.sendMessage("")
            sender.sendMessage("${ChatColor.GRAY}Total Refunds: ${ChatColor.WHITE}${refundHistory.size}")
        }

        sender.sendMessage(divider)
        sender.sendMessage("")

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
