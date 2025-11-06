package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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
        
        // Reset the rental
        plugin.getRentalManager().resetRental(regionName);
        sender.sendMessage(plugin.getConfigManager().getMessage("rental-reset", 
            "{region}", regionName));
        
        return true;
    }
}
