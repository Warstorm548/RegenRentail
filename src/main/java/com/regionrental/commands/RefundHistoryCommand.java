package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Command to view refund history for a rental region
 * Shows all refund transactions with timestamps, amounts, and reasons
 */
public class RefundHistoryCommand implements CommandExecutor {

    private final RegionRental plugin;

    public RefundHistoryCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.refundhistory")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrrefundhistory <region>");
            sender.sendMessage(ChatColor.YELLOW + "Shows refund transaction history for a rental region.");
            return true;
        }

        String regionName = args[0];

        // Get rental
        Rental rental = plugin.getRentalManager().getRental(regionName);
        if (rental == null) {
            sender.sendMessage(ChatColor.RED + "Region " + regionName + " is not currently rented!");
            sender.sendMessage(ChatColor.YELLOW + "Refund history is only available for active rentals.");
            return true;
        }

        // Get refund history
        List<Rental.RefundRecord> refundHistory = rental.getRefundHistory();
        double totalPaid = rental.getTotalPaid();
        double totalRefunded = rental.getTotalRefunded();
        double netAmount = rental.getNetRefundableAmount();

        String formattedTotal = String.format(plugin.getConfigManager().getCurrencyFormat(), totalPaid);
        String formattedRefunded = String.format(plugin.getConfigManager().getCurrencyFormat(), totalRefunded);
        String formattedNet = String.format(plugin.getConfigManager().getCurrencyFormat(), netAmount);

        // Build header
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(plugin.getConfigManager().getMessage("refund-history-header",
                "{region}", regionName));
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("");

        // Rental summary
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Rental Summary:");
        sender.sendMessage(ChatColor.GRAY + "  Player: " + ChatColor.WHITE + rental.getPlayerName());
        sender.sendMessage(ChatColor.GRAY + "  Total Paid: " + ChatColor.GREEN + formattedTotal);
        sender.sendMessage(ChatColor.GRAY + "  Total Refunded: " + ChatColor.YELLOW + formattedRefunded);
        sender.sendMessage(ChatColor.GRAY + "  Net Amount: " + ChatColor.AQUA + formattedNet);
        sender.sendMessage("");

        // Refund transactions
        if (refundHistory.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No refund transactions yet.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Refund Transactions:");
            sender.sendMessage("");

            int index = 1;
            for (Rental.RefundRecord record : refundHistory) {
                String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), record.getAmount());
                String reason = formatReason(record.getReason());

                sender.sendMessage(ChatColor.GRAY + "  " + index + ". " +
                        ChatColor.GREEN + formattedAmount + ChatColor.GRAY + " - " +
                        ChatColor.WHITE + record.getFormattedTimestamp() + ChatColor.GRAY + " - " +
                        ChatColor.YELLOW + reason +
                        ChatColor.GRAY + " (by " + ChatColor.AQUA + record.getAdminName() + ChatColor.GRAY + ")");

                index++;
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Total Refunds: " + ChatColor.WHITE + refundHistory.size());
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("");

        return true;
    }

    /**
     * Format refund reason for display
     */
    private String formatReason(String reason) {
        switch (reason) {
            case "duration_reset":
                return "Duration Reset";
            case "time_removal":
                return "Time Removal";
            case "admin_reset":
                return "Admin Reset";
            default:
                return reason;
        }
    }
}
