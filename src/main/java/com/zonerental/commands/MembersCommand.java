package com.zonerental.commands;

import com.zonerental.ZoneRental;
import com.zonerental.managers.Rental;
import com.zonerental.util.WorldRegionParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MembersCommand implements CommandExecutor, TabCompleter {

    private final ZoneRental plugin;

    public MembersCommand(ZoneRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Player-only command
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Permission check
        if (!player.hasPermission("zonerental.members")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Usage check
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /zrmembers <region>");
            player.sendMessage(ChatColor.YELLOW + "Example: /zrmembers shop1");
            player.sendMessage(ChatColor.YELLOW + "Example: /zrmembers world:shop1");
            return true;
        }

        // Parse region with world-aware format
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(args[0], player);
        if (parsed == null) {
            player.sendMessage(ChatColor.RED + "Invalid region format!");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        // Check if region exists
        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            player.sendMessage(plugin.getConfigManager().getMessage("region-not-found",
                "{region}", parsed.getCompositeKey()));
            return true;
        }

        // Get rental
        Rental rental = plugin.getRentalManager().getRental(regionName, world);
        if (rental == null) {
            player.sendMessage(ChatColor.RED + "Region " + parsed.getCompositeKey() + " is not currently rented!");
            return true;
        }

        // Display members list header
        player.sendMessage(plugin.getConfigManager().getMessage("members-list-header",
            "{region}", parsed.getCompositeKey()));

        // Get members
        if (rental.getMembers().isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("members-list-empty"));
        } else {
            // Display each member
            for (UUID memberUUID : rental.getMembers()) {
                OfflinePlayer memberPlayer = Bukkit.getOfflinePlayer(memberUUID);
                String memberName = memberPlayer.getName();
                if (memberName == null) {
                    memberName = memberUUID.toString();
                }

                // Add online/offline indicator
                String status = "";
                if (memberPlayer.isOnline()) {
                    status = ChatColor.GREEN + " (Online)";
                } else {
                    status = ChatColor.GRAY + " (Offline)";
                }

                player.sendMessage(plugin.getConfigManager().getMessage("members-list-entry",
                    "{member}", memberName + status));
            }
        }

        // Display footer with member count and limit
        int maxMembers = plugin.getConfigManager().getMaxMembers();
        String maxDisplay = maxMembers == -1 ? "∞" : String.valueOf(maxMembers);

        player.sendMessage(plugin.getConfigManager().getMessage("members-list-footer",
            "{count}", String.valueOf(rental.getMemberCount()),
            "{max}", maxDisplay));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;

            // Complete region names from all rentals (player can view any rental's members)
            List<Rental> allRentals = plugin.getRentalManager().getAllRentals();

            for (Rental rental : allRentals) {
                // Add both simple name and composite key
                if (rental.getWorldName().equals(player.getWorld().getName())) {
                    completions.add(rental.getRegionName());
                }
                completions.add(rental.getCompositeKey());
            }

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return completions;
    }
}
