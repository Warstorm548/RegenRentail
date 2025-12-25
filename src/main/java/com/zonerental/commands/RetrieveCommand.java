package com.zonerental.commands;

import com.zonerental.ZoneRental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RetrieveCommand implements CommandExecutor {
    
    private final ZoneRental plugin;

    public RetrieveCommand(ZoneRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("zonerental.retrieve")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        // Open the retrieval GUI
        plugin.getStorageManager().openRetrievalGUI(player);
        
        return true;
    }
}
