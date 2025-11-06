package com.regionrental.commands;

import com.regionrental.RegionRental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RRCommand implements CommandExecutor, TabCompleter {
    
    private final RegionRental plugin;
    
    public RRCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }
        
        // Delegate to specific commands based on first argument
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("regionrental.admin.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
                return true;
                
            case "list":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rr list <player>");
                    return true;
                }
                // Handle list command
                return true;
                
            case "info":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rr info <region>");
                    return true;
                }
                // Handle info command
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /rr help for help.");
                return true;
        }
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionRental Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/rr help" + ChatColor.WHITE + " - Show this help menu");
        sender.sendMessage(ChatColor.YELLOW + "/rr info <region>" + ChatColor.WHITE + " - View rental information");
        sender.sendMessage(ChatColor.YELLOW + "/rr list" + ChatColor.WHITE + " - List your rentals");
        sender.sendMessage(ChatColor.YELLOW + "/rrextend <region>" + ChatColor.WHITE + " - Extend a rental");
        sender.sendMessage(ChatColor.YELLOW + "/rrretrieve" + ChatColor.WHITE + " - Retrieve stored items");
        
        if (sender.hasPermission("regionrental.admin")) {
            sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/rrreload" + ChatColor.WHITE + " - Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/rrcreatesign <region>" + ChatColor.WHITE + " - Create rental sign");
            sender.sendMessage(ChatColor.YELLOW + "/rrreset <region>" + ChatColor.WHITE + " - Reset a rental");
            sender.sendMessage(ChatColor.YELLOW + "/rrretime <player> <region>" + ChatColor.WHITE + " - Reset rental time");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "info", "list");
            
            if (sender.hasPermission("regionrental.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("reload");
            }
            
            String partial = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        }
        
        return completions;
    }
}
