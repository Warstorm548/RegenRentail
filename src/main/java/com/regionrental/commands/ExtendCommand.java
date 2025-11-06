package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExtendCommand implements CommandExecutor {
    
    private final RegionRental plugin;
    
    public ExtendCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("regionrental.extend")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /rrextend <region>");
            return true;
        }
        
        String regionName = args[0];
        Rental rental = plugin.getRentalManager().getRental(regionName);
        
        if (rental == null) {
            player.sendMessage(ChatColor.RED + "Region " + regionName + " is not rented!");
            return true;
        }
        
        // Check if player owns this rental
        if (!rental.getPlayerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this rental!");
            return true;
        }
        
        // Check extension limit
        if (rental.getExtensionCount() >= plugin.getConfigManager().getMaxExtensions()) {
            player.sendMessage(plugin.getConfigManager().getMessage("max-extensions-reached"));
            return true;
        }
        
        // Get extension price
        double price = plugin.getConfigManager().getPriceForRegion(regionName);
        double multiplier = plugin.getConfig().getDouble("extension.price-multiplier", 1.0);
        price = price * multiplier;
        
        // Check economy
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy system not available!");
            return true;
        }
        
        if (!economy.has(player, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-enough-money",
                "{amount}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));
            return true;
        }
        
        // Withdraw money
        economy.withdrawPlayer(player, price);
        
        // Extend rental
        int days = plugin.getConfigManager().getExtensionDuration();
        if (plugin.getRentalManager().extendRental(regionName, player, days, price)) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-extended",
                "{region}", regionName,
                "{days}", String.valueOf(days),
                "{price}", String.format(plugin.getConfigManager().getCurrencyFormat(), price)));
        } else {
            // Refund if extension failed
            economy.depositPlayer(player, price);
            player.sendMessage(ChatColor.RED + "Failed to extend rental!");
        }
        
        return true;
    }
}
