package com.zonerental.commands;

import com.zonerental.ZoneRental;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateSignCommand implements CommandExecutor {
    
    private final ZoneRental plugin;

    public CreateSignCommand(ZoneRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("zonerental.admin.createsign")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /zrcreatesign <region>");
            return true;
        }
        
        String regionName = args[0];

        // Check if region exists in player's current world
        if (!plugin.getWorldGuardManager().regionExists(regionName, player.getWorld())) {
            player.sendMessage(plugin.getConfigManager().getMessage("region-not-found",
                "{region}", regionName));
            return true;
        }
        
        // Get the block the player is looking at
        Block targetBlock = player.getTargetBlock(null, 5);
        
        if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a sign!");
            return true;
        }
        
        // Create the rental sign
        plugin.getSignManager().createSign(regionName, targetBlock.getLocation());

        player.sendMessage(plugin.getConfigManager().getMessage("sign-created",
            "{region}", regionName));
        player.sendMessage(ChatColor.GRAY + "Use /zroverride to set custom rental settings for this region.");

        return true;
    }
}
