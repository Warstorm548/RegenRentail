package com.regionrental.commands;

import com.regionrental.RegionRental;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    
    private final RegionRental plugin;
    
    public ReloadCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.reload")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        plugin.reloadPlugin();
        sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
        return true;
    }
}
