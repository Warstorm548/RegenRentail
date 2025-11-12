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

        // Reset the rental with full refund
        Map<String, Object> refundDetails = plugin.getRentalManager().resetRentalWithRefund(regionName);

        if (refundDetails != null) {
            String playerName = (String) refundDetails.get("playerName");
            double refundAmount = (double) refundDetails.get("refundAmount");
            String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), refundAmount);

            // Send success message to admin with refund details
            sender.sendMessage(plugin.getConfigManager().getMessage("admin-reset-success",
                "{region}", regionName,
                "{player}", playerName,
                "{amount}", formattedAmount));

            // Log the action
            plugin.getLogger().info("Admin " + sender.getName() + " reset rental for region " + regionName +
                                  ". Refunded " + formattedAmount + " to " + playerName);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to reset rental for region " + regionName);
        }

        return true;
    }
}
