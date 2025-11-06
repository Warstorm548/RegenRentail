package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class InfoCommand implements CommandExecutor {
    
    private final RegionRental plugin;
    
    public InfoCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.info")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrinfo <region>");
            return true;
        }
        
        String regionName = args[0];
        
        // Check if region exists
        if (!plugin.getWorldGuardManager().regionExists(regionName)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("region-not-found", 
                "{region}", regionName));
            return true;
        }
        
        Rental rental = plugin.getRentalManager().getRental(regionName);
        
        sender.sendMessage(ChatColor.GOLD + "=== Region: " + regionName + " ===");
        
        if (rental == null) {
            sender.sendMessage(ChatColor.GREEN + "Status: " + ChatColor.WHITE + "Available");
            sender.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + 
                String.format(plugin.getConfigManager().getCurrencyFormat(), 
                    plugin.getConfigManager().getPriceForRegion(regionName)));
            sender.sendMessage(ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + 
                plugin.getConfigManager().getDurationForRegion(regionName) + " days");
        } else {
            sender.sendMessage(ChatColor.RED + "Status: " + ChatColor.WHITE + "Rented");
            sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + rental.getPlayerName());
            sender.sendMessage(ChatColor.YELLOW + "Expires: " + ChatColor.WHITE + rental.getFormattedEndDate());
            sender.sendMessage(ChatColor.YELLOW + "Time Remaining: " + ChatColor.WHITE + 
                rental.getDaysRemaining() + " days, " + 
                (rental.getHoursRemaining() % 24) + " hours");
            sender.sendMessage(ChatColor.YELLOW + "Extensions Used: " + ChatColor.WHITE + 
                rental.getExtensionCount() + "/" + plugin.getConfigManager().getMaxExtensions());
            sender.sendMessage(ChatColor.YELLOW + "Total Paid: " + ChatColor.WHITE + 
                String.format(plugin.getConfigManager().getCurrencyFormat(), rental.getTotalPaid()));
        }
        
        return true;
    }
}
