package com.zonerental.commands;

import com.zonerental.ZoneRental;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    
    private final ZoneRental plugin;

    public ReloadCommand(ZoneRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zonerental.admin.reload")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        plugin.reloadPlugin();
        sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
        return true;
    }
}
