package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.util.WorldRegionParser;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OverrideCommand implements CommandExecutor, TabCompleter {

    private final RegionRental plugin;

    public OverrideCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.override")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "price":
                return handlePrice(sender, args);
            case "duration":
                return handleDuration(sender, args);
            case "maxextensions":
                return handleMaxExtensions(sender, args);
            case "extensionprice":
                return handleExtensionPrice(sender, args);
            case "allowextensions":
                return handleAllowExtensions(sender, args);
            case "extensionduration":
                return handleExtensionDuration(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender, args);
            default:
                showUsage(sender);
                return true;
        }
    }

    private boolean handlePrice(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride price <group_name|world:region> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "Example: /rroverride price shop_group 500");
            sender.sendMessage(ChatColor.YELLOW + "Example: /rroverride price world:shop1 500");
            return true;
        }

        String target = args[1];
        double price;

        try {
            price = Double.parseDouble(args[2]);
            if (price < 0) {
                sender.sendMessage(ChatColor.RED + "Price must be a positive number!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid price amount!");
            return true;
        }

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            // Set group override
            plugin.getRegionsConfig().setGroupPrice(target, price);

            // Mark all signs in group as dirty
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            String formattedPrice = String.format(plugin.getConfigManager().getCurrencyFormat(), price);
            sender.sendMessage(ChatColor.GREEN + "Set rental price for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set price override for group " + target + " to " + price);
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            sender.sendMessage(ChatColor.YELLOW + "Groups: " + String.join(", ", plugin.getGroupsConfig().getAllGroups()));
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        // Validate region exists in WorldGuard
        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride price " + groupName + " " + args[2]);
            return true;
        }

        // Set individual region override
        plugin.getRegionsConfig().setRegionPrice(regionName, world, price);

        // Mark sign dirty to update with new price
        plugin.getSignManager().markSignDirty(regionName, world);

        String formattedPrice = String.format(plugin.getConfigManager().getCurrencyFormat(), price);
        sender.sendMessage(ChatColor.GREEN + "Set rental price for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);

        plugin.getLogger().info(sender.getName() + " set price override for region " + compositeKey + " to " + price);
        return true;
    }

    private boolean handleDuration(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride duration <group_name|world:region> <days>");
            return true;
        }

        String target = args[1];
        int days;

        try {
            days = Integer.parseInt(args[2]);
            if (days <= 0) {
                sender.sendMessage(ChatColor.RED + "Duration must be a positive number!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration!");
            return true;
        }

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            plugin.getRegionsConfig().setGroupDuration(target, days);
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            sender.sendMessage(ChatColor.GREEN + "Set rental duration for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set duration override for group " + target + " to " + days + " days");
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride duration " + groupName + " " + args[2]);
            return true;
        }

        plugin.getRegionsConfig().setRegionDuration(regionName, world, days);
        plugin.getSignManager().markSignDirty(regionName, world);

        sender.sendMessage(ChatColor.GREEN + "Set rental duration for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");

        plugin.getLogger().info(sender.getName() + " set duration override for region " + compositeKey + " to " + days + " days");
        return true;
    }

    private boolean handleMaxExtensions(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride maxextensions <group_name|world:region> <count>");
            return true;
        }

        String target = args[1];
        int maxExtensions;

        try {
            maxExtensions = Integer.parseInt(args[2]);
            if (maxExtensions < 0) {
                sender.sendMessage(ChatColor.RED + "Max extensions must be 0 or greater!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number!");
            return true;
        }

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            plugin.getRegionsConfig().setGroupMaxExtensions(target, maxExtensions);
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            sender.sendMessage(ChatColor.GREEN + "Set max extensions for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + maxExtensions);
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set max-extensions override for group " + target + " to " + maxExtensions);
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride maxextensions " + groupName + " " + args[2]);
            return true;
        }

        plugin.getRegionsConfig().setRegionMaxExtensions(regionName, world, maxExtensions);
        plugin.getSignManager().markSignDirty(regionName, world);

        sender.sendMessage(ChatColor.GREEN + "Set max extensions for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + maxExtensions);

        plugin.getLogger().info(sender.getName() + " set max-extensions override for region " + compositeKey + " to " + maxExtensions);
        return true;
    }

    private boolean handleExtensionPrice(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride extensionprice <group_name|world:region> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "Tip: Use 0 to auto-calculate from rental price and duration");
            return true;
        }

        String target = args[1];
        double price;

        try {
            price = Double.parseDouble(args[2]);
            if (price < 0) {
                sender.sendMessage(ChatColor.RED + "Extension price must be 0 or greater!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid price amount!");
            return true;
        }

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            plugin.getRegionsConfig().setGroupExtensionPrice(target, price);
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            String formattedPrice = price == 0 ? "auto-calculated" :
                    String.format(plugin.getConfigManager().getCurrencyFormat(), price);
            sender.sendMessage(ChatColor.GREEN + "Set extension price for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set extension-price override for group " + target + " to " + price);
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride extensionprice " + groupName + " " + args[2]);
            return true;
        }

        plugin.getRegionsConfig().setRegionExtensionPrice(regionName, world, price);
        plugin.getSignManager().markSignDirty(regionName, world);

        String formattedPrice = price == 0 ? "auto-calculated" :
                String.format(plugin.getConfigManager().getCurrencyFormat(), price);
        sender.sendMessage(ChatColor.GREEN + "Set extension price for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);

        plugin.getLogger().info(sender.getName() + " set extension-price override for region " + compositeKey + " to " + price);
        return true;
    }

    private boolean handleAllowExtensions(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride allowextensions <group_name|world:region> <true|false>");
            return true;
        }

        String target = args[1];
        String allowStr = args[2].toLowerCase();

        if (!allowStr.equals("true") && !allowStr.equals("false")) {
            sender.sendMessage(ChatColor.RED + "Value must be 'true' or 'false'!");
            return true;
        }

        boolean allow = Boolean.parseBoolean(allowStr);

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            plugin.getRegionsConfig().setGroupAllowExtensions(target, allow);
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            sender.sendMessage(ChatColor.GREEN + "Set allow extensions for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + allow);
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set allow-extensions override for group " + target + " to " + allow);
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride allowextensions " + groupName + " " + args[2]);
            return true;
        }

        plugin.getRegionsConfig().setRegionAllowExtensions(regionName, world, allow);
        plugin.getSignManager().markSignDirty(regionName, world);

        sender.sendMessage(ChatColor.GREEN + "Set allow extensions for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + allow);

        plugin.getLogger().info(sender.getName() + " set allow-extensions override for region " + compositeKey + " to " + allow);
        return true;
    }

    private boolean handleExtensionDuration(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride extensionduration <group_name|world:region> <days>");
            return true;
        }

        String target = args[1];
        int days;

        try {
            days = Integer.parseInt(args[2]);
            if (days <= 0) {
                sender.sendMessage(ChatColor.RED + "Extension duration must be a positive number!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration!");
            return true;
        }

        // Check if target is a group
        if (plugin.getGroupsConfig().groupExists(target)) {
            plugin.getRegionsConfig().setGroupExtensionDuration(target, days);
            List<String> groupRegions = plugin.getGroupsConfig().getGroupRegions(target);
            plugin.getSignManager().bulkMarkSignsDirty(groupRegions);

            sender.sendMessage(ChatColor.GREEN + "Set extension duration for group " + ChatColor.YELLOW + target +
                    ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");
            sender.sendMessage(ChatColor.GRAY + "Updated " + groupRegions.size() + " region(s) in group");

            plugin.getLogger().info(sender.getName() + " set extension-duration override for group " + target + " to " + days + " days");
            return true;
        }

        // Not a group, try as region
        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not exist in world '" + world.getName() + "'!");
            return true;
        }

        // Check if region is in a group
        String compositeKey = parsed.getCompositeKey();
        if (plugin.getGroupsConfig().isRegionInGroup(compositeKey)) {
            String groupName = plugin.getGroupsConfig().getRegionGroup(compositeKey);
            sender.sendMessage(ChatColor.RED + "Region " + ChatColor.YELLOW + compositeKey +
                    ChatColor.RED + " is in group " + ChatColor.YELLOW + groupName);
            sender.sendMessage(ChatColor.YELLOW + "Use: /rroverride extensionduration " + groupName + " " + args[2]);
            return true;
        }

        plugin.getRegionsConfig().setRegionExtensionDuration(regionName, world, days);
        plugin.getSignManager().markSignDirty(regionName, world);

        sender.sendMessage(ChatColor.GREEN + "Set extension duration for " + ChatColor.YELLOW + compositeKey +
                ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");

        plugin.getLogger().info(sender.getName() + " set extension-duration override for region " + compositeKey + " to " + days + " days");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride remove <world:region>");
            return true;
        }

        WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(args[1], sender);
        if (parsed == null) {
            sender.sendMessage(ChatColor.RED + "Invalid region format! Console must use world:region format");
            return true;
        }

        World world = parsed.getWorld();
        String regionName = parsed.regionName;

        if (!plugin.getRegionsConfig().hasRegion(regionName, world)) {
            sender.sendMessage(ChatColor.RED + "Region '" + parsed.getCompositeKey() + "' has no custom overrides!");
            return true;
        }

        plugin.getRegionsConfig().removeRegion(regionName, world);

        sender.sendMessage(ChatColor.GREEN + "Removed all custom overrides for " + ChatColor.YELLOW + parsed.getCompositeKey());
        sender.sendMessage(ChatColor.YELLOW + "Region will now use default settings from config.yml");

        // Mark sign dirty to update with default values
        plugin.getSignManager().markSignDirty(regionName, world);

        plugin.getLogger().info(sender.getName() + " removed overrides for region " + parsed.getCompositeKey());
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // List all groups and regions with overrides
            java.util.Set<String> groups = plugin.getRegionsConfig().getAllGroupsWithOverrides();
            java.util.Set<String> regions = plugin.getRegionsConfig().getAllRegions();

            if (groups.isEmpty() && regions.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No groups or regions have custom overrides configured");
                sender.sendMessage(ChatColor.GRAY + "Use /rroverride <setting> <group|world:region> <value> to set overrides");
                return true;
            }

            if (!groups.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "=== Groups with Custom Overrides ===");
                for (String group : groups) {
                    int regionCount = plugin.getGroupsConfig().getGroupSize(group);
                    sender.sendMessage(ChatColor.YELLOW + "  - " + group +
                            ChatColor.GRAY + " (" + regionCount + " regions) " +
                            ChatColor.DARK_GRAY + "(/rroverride list " + group + ")");
                }
            }

            if (!regions.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "=== Regions with Custom Overrides ===");
                for (String compositeKey : regions) {
                    sender.sendMessage(ChatColor.YELLOW + "  - " + compositeKey +
                            ChatColor.DARK_GRAY + " (/rroverride list " + compositeKey + ")");
                }
            }

            sender.sendMessage(ChatColor.GRAY + "Total: " + groups.size() + " group(s), " + regions.size() + " region(s)");
            return true;
        } else if (args.length == 2) {
            String target = args[1];

            // Check if target is a group
            if (plugin.getGroupsConfig().groupExists(target)) {
                if (!plugin.getRegionsConfig().hasGroupOverrides(target)) {
                    sender.sendMessage(ChatColor.RED + "Group '" + target + "' has no custom overrides");
                    sender.sendMessage(ChatColor.YELLOW + "Using default values from config.yml");
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + "=== Custom Overrides for Group: " + target + " ===");

                Map<String, Object> overrides = plugin.getRegionsConfig().getGroupOverrides(target);

                if (overrides.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No overrides set (using defaults)");
                    return true;
                }

                for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    sender.sendMessage(ChatColor.YELLOW + "  " + key + ": " + ChatColor.WHITE + value);
                }

                int regionCount = plugin.getGroupsConfig().getGroupSize(target);
                sender.sendMessage(ChatColor.GRAY + "Applies to " + regionCount + " region(s) in group");

                return true;
            }

            // Not a group, try as region
            WorldRegionParser.ParsedRegion parsed = WorldRegionParser.parse(target, sender);
            if (parsed == null) {
                sender.sendMessage(ChatColor.RED + "Invalid target! Must be a group name or world:region format");
                return true;
            }

            World world = parsed.getWorld();
            String regionName = parsed.regionName;

            if (!plugin.getRegionsConfig().hasRegion(regionName, world)) {
                sender.sendMessage(ChatColor.RED + "Region '" + parsed.getCompositeKey() + "' has no custom overrides");
                sender.sendMessage(ChatColor.YELLOW + "Using default values from config.yml");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Custom Overrides for " + parsed.getCompositeKey() + " ===");

            Map<String, Object> overrides = plugin.getRegionsConfig().getRegionOverrides(regionName, world);

            if (overrides.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No overrides set (using defaults)");
                return true;
            }

            for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.equals("_comment")) continue; // Skip comment field

                sender.sendMessage(ChatColor.YELLOW + "  " + key + ": " + ChatColor.WHITE + value);
            }

            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride list [group_name|world:region]");
            return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionRental Override Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride price <group|region> <amount>");
        sender.sendMessage(ChatColor.GRAY + "  Set custom rental price for group or region");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride duration <group|region> <days>");
        sender.sendMessage(ChatColor.GRAY + "  Set custom rental duration");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride maxextensions <group|region> <count>");
        sender.sendMessage(ChatColor.GRAY + "  Set max number of extensions allowed");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride extensionprice <group|region> <amount>");
        sender.sendMessage(ChatColor.GRAY + "  Set extension price (0 for auto-calculate)");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride allowextensions <group|region> <true|false>");
        sender.sendMessage(ChatColor.GRAY + "  Enable or disable extensions");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride extensionduration <group|region> <days>");
        sender.sendMessage(ChatColor.GRAY + "  Set extension duration in days");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride remove <region>");
        sender.sendMessage(ChatColor.GRAY + "  Remove all region overrides (use defaults)");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride list [group|region]");
        sender.sendMessage(ChatColor.GRAY + "  List all overrides or show details");
        sender.sendMessage(ChatColor.GRAY + "Note: Regions in groups must use group overrides");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("regionrental.admin.override")) {
            return completions;
        }

        if (args.length == 1) {
            // Subcommand suggestions
            List<String> subCommands = Arrays.asList("price", "duration", "maxextensions",
                    "extensionprice", "allowextensions", "extensionduration", "remove", "list");
            String partial = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            // Region name suggestions - get all WorldGuard regions
            String partial = args[1].toLowerCase();
            for (String region : plugin.getWorldGuardManager().getAllRegionNames()) {
                if (region.toLowerCase().startsWith(partial)) {
                    completions.add(region);
                }
            }
        } else if (args.length == 3) {
            // Value suggestions based on subcommand
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "price":
                case "extensionprice":
                    completions.addAll(Arrays.asList("10", "50", "100", "500", "1000"));
                    break;
                case "duration":
                case "extensionduration":
                    completions.addAll(Arrays.asList("1", "3", "7", "14", "30"));
                    break;
                case "maxextensions":
                    completions.addAll(Arrays.asList("0", "5", "10", "20", "unlimited"));
                    break;
                case "allowextensions":
                    completions.addAll(Arrays.asList("true", "false"));
                    break;
            }
        }

        return completions;
    }
}
