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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MemberCommand implements CommandExecutor, TabCompleter {

    private final ZoneRental plugin;

    public MemberCommand(ZoneRental plugin) {
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
        if (!player.hasPermission("zonerental.member")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Check if member management is enabled
        if (!plugin.getConfigManager().isMemberManagementEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("member-management-disabled"));
            return true;
        }

        // Usage check
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /zrmember <add|remove> <region> <username>");
            player.sendMessage(ChatColor.YELLOW + "Example: /zrmember add shop1 PlayerName");
            player.sendMessage(ChatColor.YELLOW + "Example: /zrmember remove world:shop1 PlayerName");
            return true;
        }

        String action = args[0].toLowerCase();
        String memberName = args[2];

        // Validate action
        if (!action.equals("add") && !action.equals("remove")) {
            player.sendMessage(ChatColor.RED + "Invalid action! Use 'add' or 'remove'");
            return true;
        }

        // Parse region with world-aware format
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(args[1], player);
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

        // Check if player owns the rental
        if (!rental.getPlayerUUID().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-rental-owner"));
            return true;
        }

        // Resolve username to UUID
        OfflinePlayer memberPlayer = Bukkit.getOfflinePlayer(memberName);
        UUID memberUUID = memberPlayer.getUniqueId();

        // Check if player is trying to add themselves
        if (memberUUID.equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("cannot-add-self"));
            return true;
        }

        // Handle action
        if (action.equals("add")) {
            // Check if member already exists
            if (rental.hasMember(memberUUID)) {
                player.sendMessage(plugin.getConfigManager().getMessage("member-already-added",
                    "{member}", memberPlayer.getName()));
                return true;
            }

            // Check member limit
            int maxMembers = plugin.getConfigManager().getMaxMembers();
            if (maxMembers != -1 && rental.getMemberCount() >= maxMembers) {
                player.sendMessage(plugin.getConfigManager().getMessage("member-limit-reached",
                    "{current}", String.valueOf(rental.getMemberCount()),
                    "{max}", String.valueOf(maxMembers)));
                return true;
            }

            // Add member
            if (plugin.getRentalManager().addMemberToRental(regionName, world, player.getUniqueId(), memberUUID)) {
                player.sendMessage(plugin.getConfigManager().getMessage("member-added",
                    "{member}", memberPlayer.getName(),
                    "{region}", parsed.getCompositeKey()));

                // Notify member if online
                if (memberPlayer.isOnline() && memberPlayer.getPlayer() != null) {
                    memberPlayer.getPlayer().sendMessage(plugin.getConfigManager().getMessage("member-added-notify",
                        "{region}", parsed.getCompositeKey(),
                        "{owner}", player.getName()));
                }
            } else {
                player.sendMessage(ChatColor.RED + "Failed to add member!");
            }
        } else if (action.equals("remove")) {
            // Check if member exists
            if (!rental.hasMember(memberUUID)) {
                player.sendMessage(plugin.getConfigManager().getMessage("member-not-found",
                    "{member}", memberPlayer.getName()));
                return true;
            }

            // Remove member
            if (plugin.getRentalManager().removeMemberFromRental(regionName, world, player.getUniqueId(), memberUUID)) {
                player.sendMessage(plugin.getConfigManager().getMessage("member-removed",
                    "{member}", memberPlayer.getName(),
                    "{region}", parsed.getCompositeKey()));

                // Notify member if online
                if (memberPlayer.isOnline() && memberPlayer.getPlayer() != null) {
                    memberPlayer.getPlayer().sendMessage(plugin.getConfigManager().getMessage("member-removed-notify",
                        "{region}", parsed.getCompositeKey(),
                        "{owner}", player.getName()));
                }
            } else {
                player.sendMessage(ChatColor.RED + "Failed to remove member!");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete action: add, remove
            completions.addAll(Arrays.asList("add", "remove"));
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && sender instanceof Player) {
            // Complete region names from player's rentals
            Player player = (Player) sender;
            List<Rental> playerRentals = plugin.getRentalManager().getPlayerRentals(player.getUniqueId());

            for (Rental rental : playerRentals) {
                // Add both simple name and composite key
                completions.add(rental.getRegionName());
                completions.add(rental.getCompositeKey());
            }

            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 3) {
            // Complete online player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return completions;
    }
}
