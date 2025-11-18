package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Command to completely remove RegionRental setup from a region
 * This removes signs, schematics, and configuration data
 * Useful when a region needs to be repurposed or is no longer needed for rentals
 */
public class RemoveCommand implements CommandExecutor {

    private final RegionRental plugin;

    public RemoveCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.remove")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrremove <region>");
            sender.sendMessage(ChatColor.YELLOW + "This will completely remove RegionRental setup from the region.");
            sender.sendMessage(ChatColor.YELLOW + "If the region is currently rented, the rental will be reset with full refund.");
            return true;
        }

        String regionName = args[0];

        // Check if WorldGuard region exists
        if (!plugin.getWorldGuardManager().regionExists(regionName)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("region-not-found",
                "{region}", regionName));
            return true;
        }

        // Check if region has an active rental
        Rental rental = plugin.getRentalManager().getRental(regionName);
        if (rental != null) {
            // Reset the rental with net refund first (prevents double-refunds)
            Map<String, Object> refundDetails = plugin.getRentalManager().resetRentalWithRefund(regionName);

            if (refundDetails != null) {
                String playerName = (String) refundDetails.get("playerName");
                double refundAmount = (double) refundDetails.get("refundAmount");
                double totalPaid = (double) refundDetails.get("totalPaid");
                double alreadyRefunded = (double) refundDetails.get("alreadyRefunded");

                String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), refundAmount);
                String formattedTotal = String.format(plugin.getConfigManager().getCurrencyFormat(), totalPaid);
                String formattedAlready = String.format(plugin.getConfigManager().getCurrencyFormat(), alreadyRefunded);

                sender.sendMessage(ChatColor.YELLOW + "Active rental found. Player " + playerName +
                        " has been refunded " + formattedAmount);

                // Show refund breakdown if any previous refunds exist
                if (alreadyRefunded > 0) {
                    sender.sendMessage(ChatColor.GRAY + "  Total paid: " + ChatColor.YELLOW + formattedTotal);
                    sender.sendMessage(ChatColor.GRAY + "  Already refunded: " + ChatColor.YELLOW + formattedAlready);
                    sender.sendMessage(ChatColor.GRAY + "  Net refund: " + ChatColor.GREEN + formattedAmount);
                }
            }
        }

        // Remove rental sign
        boolean signRemoved = plugin.getSignManager().removeRegionSetup(regionName);

        // Delete WorldEdit schematic if it exists
        boolean schematicDeleted = false;
        if (plugin.getWorldEditManager().hasCapture(regionName)) {
            plugin.getWorldEditManager().deleteCapture(regionName);
            schematicDeleted = true;
        }

        // Send comprehensive success message
        StringBuilder message = new StringBuilder();
        message.append(plugin.getConfigManager().getMessage("region-removed",
            "{region}", regionName));

        if (signRemoved) {
            message.append("\n").append(ChatColor.GREEN).append("  ✓ Rental sign removed");
        }

        if (schematicDeleted) {
            message.append("\n").append(ChatColor.GREEN).append("  ✓ WorldEdit schematic deleted");
        }

        if (rental != null) {
            message.append("\n").append(ChatColor.GREEN).append("  ✓ Active rental reset with refund");
        }

        sender.sendMessage(message.toString());

        // Log the action
        plugin.getLogger().info("Admin " + sender.getName() + " removed RegionRental setup from region: " + regionName);

        return true;
    }
}
