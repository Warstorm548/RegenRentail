package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class ResetCommand implements CommandExecutor {

    private final RegionRental plugin;

    public ResetCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.reset")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrreset <region>");
            return true;
        }

        String regionName = args[0];

        Rental rental = plugin.getRentalManager().getRental(regionName);
        if (rental == null) {
            sender.sendMessage(ChatColor.RED + "Region " + regionName + " is not currently rented!");
            return true;
        }

        // Reset the rental with net refund (prevents double-refunds)
        Map<String, Object> refundDetails = plugin.getRentalManager().resetRentalWithRefund(regionName);

        if (refundDetails != null) {
            String playerName = (String) refundDetails.get("playerName");
            double refundAmount = (double) refundDetails.get("refundAmount");
            double totalPaid = (double) refundDetails.get("totalPaid");
            double alreadyRefunded = (double) refundDetails.get("alreadyRefunded");

            String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), refundAmount);
            String formattedTotal = String.format(plugin.getConfigManager().getCurrencyFormat(), totalPaid);
            String formattedAlready = String.format(plugin.getConfigManager().getCurrencyFormat(), alreadyRefunded);

            // Send success message to admin with detailed refund information
            sender.sendMessage(plugin.getConfigManager().getMessage("admin-reset-success",
                    "{region}", regionName,
                    "{player}", playerName,
                    "{amount}", formattedAmount));

            // Show refund breakdown if any previous refunds exist
            if (alreadyRefunded > 0) {
                sender.sendMessage(ChatColor.GRAY + "  Total paid: " + ChatColor.YELLOW + formattedTotal);
                sender.sendMessage(ChatColor.GRAY + "  Already refunded: " + ChatColor.YELLOW + formattedAlready);
                sender.sendMessage(ChatColor.GRAY + "  Net refund: " + ChatColor.GREEN + formattedAmount);
            }

            // Log the action
            plugin.getLogger().info("Admin " + sender.getName() + " reset rental for region " + regionName +
                    ". Refunded " + formattedAmount + " to " + playerName +
                    (alreadyRefunded > 0 ? " (Total paid: " + formattedTotal + ", Already refunded: " + formattedAlready + ")" : ""));
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to reset rental for region " + regionName);
        }

        return true;
    }
}
