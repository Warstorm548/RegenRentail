package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ListCommand implements CommandExecutor {
    
    private final RegionRental plugin;
    
    public ListCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.list")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        UUID targetUUID = null;
        String targetName = "Your";
        
        // If player specified, check admin permission
        if (args.length >= 1) {
            if (!sender.hasPermission("regionrental.admin.list.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to view other players' rentals!");
                return true;
            }
            
            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
            
            if (!targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            
            targetUUID = targetPlayer.getUniqueId();
            targetName = targetPlayer.getName() + "'s";
        } else if (sender instanceof Player) {
            targetUUID = ((Player) sender).getUniqueId();
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /rrlist <player>");
            return true;
        }
        
        List<Rental> rentals = plugin.getRentalManager().getPlayerRentals(targetUUID);
        
        if (rentals.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + targetName + " active rentals: " + ChatColor.WHITE + "None");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== " + targetName + " Active Rentals ===");
        
        for (Rental rental : rentals) {
            String status = rental.isExpired() ? ChatColor.RED + "EXPIRED" : ChatColor.GREEN + "ACTIVE";
            sender.sendMessage(ChatColor.YELLOW + "• " + rental.getRegionName() + 
                " - " + status + ChatColor.WHITE + " - Expires: " + rental.getFormattedEndDate() + 
                " (" + rental.getDaysRemaining() + " days remaining)");
        }
        
        sender.sendMessage(ChatColor.GRAY + "Total rentals: " + rentals.size() + "/" + 
            plugin.getConfigManager().getMaxRentalsPerPlayer());
        
        return true;
    }
}
