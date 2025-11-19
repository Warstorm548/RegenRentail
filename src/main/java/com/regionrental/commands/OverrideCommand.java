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
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride price <region> <amount>");
            return true;
        }

        String region = args[1];
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

        // Validate region exists in WorldGuard
        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionPrice(region, price);

        String formattedPrice = String.format(plugin.getConfigManager().getCurrencyFormat(), price);
        sender.sendMessage(ChatColor.GREEN + "Set rental price for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);

        // Update sign if it exists
        plugin.getSignManager().updateSign(region);

        plugin.getLogger().info(sender.getName() + " set price override for region " + region + " to " + price);
        return true;
    }

    private boolean handleDuration(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride duration <region> <days>");
            return true;
        }

        String region = args[1];
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

        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionDuration(region, days);

        sender.sendMessage(ChatColor.GREEN + "Set rental duration for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");

        plugin.getSignManager().updateSign(region);

        plugin.getLogger().info(sender.getName() + " set duration override for region " + region + " to " + days + " days");
        return true;
    }

    private boolean handleMaxExtensions(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride maxextensions <region> <count>");
            return true;
        }

        String region = args[1];
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

        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionMaxExtensions(region, maxExtensions);

        sender.sendMessage(ChatColor.GREEN + "Set max extensions for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + maxExtensions);

        plugin.getLogger().info(sender.getName() + " set max-extensions override for region " + region + " to " + maxExtensions);
        return true;
    }

    private boolean handleExtensionPrice(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride extensionprice <region> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "Tip: Use 0 to auto-calculate from rental price and duration");
            return true;
        }

        String region = args[1];
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

        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionExtensionPrice(region, price);

        String formattedPrice = price == 0 ? "auto-calculated" :
                String.format(plugin.getConfigManager().getCurrencyFormat(), price);
        sender.sendMessage(ChatColor.GREEN + "Set extension price for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + formattedPrice);

        plugin.getLogger().info(sender.getName() + " set extension-price override for region " + region + " to " + price);
        return true;
    }

    private boolean handleAllowExtensions(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride allowextensions <region> <true|false>");
            return true;
        }

        String region = args[1];
        String allowStr = args[2].toLowerCase();

        if (!allowStr.equals("true") && !allowStr.equals("false")) {
            sender.sendMessage(ChatColor.RED + "Value must be 'true' or 'false'!");
            return true;
        }

        boolean allow = Boolean.parseBoolean(allowStr);

        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionAllowExtensions(region, allow);

        sender.sendMessage(ChatColor.GREEN + "Set allow extensions for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + allow);

        plugin.getLogger().info(sender.getName() + " set allow-extensions override for region " + region + " to " + allow);
        return true;
    }

    private boolean handleExtensionDuration(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride extensionduration <region> <days>");
            return true;
        }

        String region = args[1];
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

        if (!plugin.getWorldGuardManager().doesRegionExist(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist in WorldGuard!");
            return true;
        }

        plugin.getRegionsConfig().setRegionExtensionDuration(region, days);

        sender.sendMessage(ChatColor.GREEN + "Set extension duration for " + ChatColor.YELLOW + region +
                ChatColor.GREEN + " to " + ChatColor.GOLD + days + " days");

        plugin.getLogger().info(sender.getName() + " set extension-duration override for region " + region + " to " + days + " days");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride remove <region>");
            return true;
        }

        String region = args[1];

        if (!plugin.getRegionsConfig().hasRegion(region)) {
            sender.sendMessage(ChatColor.RED + "Region '" + region + "' has no custom overrides!");
            return true;
        }

        plugin.getRegionsConfig().removeRegion(region);

        sender.sendMessage(ChatColor.GREEN + "Removed all custom overrides for " + ChatColor.YELLOW + region);
        sender.sendMessage(ChatColor.YELLOW + "Region will now use default settings from config.yml");

        // Update sign to show default values
        plugin.getSignManager().updateSign(region);

        plugin.getLogger().info(sender.getName() + " removed overrides for region " + region);
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // List all regions with overrides
            java.util.Set<String> regions = plugin.getRegionsConfig().getAllRegions();

            if (regions.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No regions have custom overrides configured");
                sender.sendMessage(ChatColor.GRAY + "Use /rroverride <setting> <region> <value> to set overrides");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Regions with Custom Overrides ===");
            for (String region : regions) {
                sender.sendMessage(ChatColor.YELLOW + "  - " + region + ChatColor.GRAY + " (/rroverride list " + region + ")");
            }
            sender.sendMessage(ChatColor.GRAY + "Total: " + regions.size() + " region(s)");
            return true;
        } else if (args.length == 2) {
            // Show details for specific region
            String region = args[1];

            if (!plugin.getRegionsConfig().hasRegion(region)) {
                sender.sendMessage(ChatColor.RED + "Region '" + region + "' has no custom overrides");
                sender.sendMessage(ChatColor.YELLOW + "Using default values from config.yml");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Custom Overrides for " + region + " ===");

            // Get the raw config to check which values are actually set
            Map<String, Object> overrides = plugin.getRegionsConfig().getRegionOverrides(region);

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
            sender.sendMessage(ChatColor.RED + "Usage: /rroverride list [region]");
            return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionRental Override Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride price <region> <amount>");
        sender.sendMessage(ChatColor.GRAY + "  Set custom rental price for region");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride duration <region> <days>");
        sender.sendMessage(ChatColor.GRAY + "  Set custom rental duration");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride maxextensions <region> <count>");
        sender.sendMessage(ChatColor.GRAY + "  Set max number of extensions allowed");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride extensionprice <region> <amount>");
        sender.sendMessage(ChatColor.GRAY + "  Set extension price (0 for auto-calculate)");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride allowextensions <region> <true|false>");
        sender.sendMessage(ChatColor.GRAY + "  Enable or disable extensions");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride extensionduration <region> <days>");
        sender.sendMessage(ChatColor.GRAY + "  Set extension duration in days");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride remove <region>");
        sender.sendMessage(ChatColor.GRAY + "  Remove all overrides (use defaults)");
        sender.sendMessage(ChatColor.YELLOW + "/rroverride list [region]");
        sender.sendMessage(ChatColor.GRAY + "  List all overrides or show details for region");
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
