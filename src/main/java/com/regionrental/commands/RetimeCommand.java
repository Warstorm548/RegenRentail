package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RetimeCommand implements CommandExecutor {
    
    private final RegionRental plugin;
    
    public RetimeCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.retime")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrretime <player> <region> [days]");
            return true;
        }
        
        String playerName = args[0];
        String regionName = args[1];
        int days = plugin.getConfigManager().getDefaultDuration();
        
        if (args.length >= 3) {
            try {
                days = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number of days!");
                return true;
            }
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        
        if (!targetPlayer.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found!");
            return true;
        }
        
        Rental rental = plugin.getRentalManager().getRental(regionName);
        if (rental == null) {
            sender.sendMessage(ChatColor.RED + "Region " + regionName + " is not currently rented!");
            return true;
        }
        
        if (!rental.getPlayerUUID().equals(targetPlayer.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " does not rent region " + regionName + "!");
            return true;
        }
        
        // Reset the rental time
        plugin.getRentalManager().resetRentalTime(regionName, days);
        sender.sendMessage(ChatColor.GREEN + "Reset rental time for " + regionName + " to " + days + " days");
        
        return true;
    }
}
