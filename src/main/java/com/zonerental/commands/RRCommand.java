package com.zonerental.commands;

import com.zonerental.ZoneRental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RRCommand implements CommandExecutor, TabCompleter {

    private final ZoneRental plugin;

    public RRCommand(ZoneRental plugin) {
        this.plugin = plugin;
    }

    /**
     * Helper method to get the current active prefix
     */
    private String getPrefix() {
        return plugin.getActivePrefix();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle help command with various options
        if (args.length == 0) {
            showHelp(sender, 1);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            if (args.length == 1) {
                // /rr help - show page 1
                showHelp(sender, 1);
                return true;
            }

            // Validate args.length >= 2 before accessing args[1]
            if (args.length < 2) {
                showHelp(sender, 1);
                return true;
            }

            // Check if argument is a page number
            try {
                int page = Integer.parseInt(args[1]);
                if (page >= 1 && page <= 3) {
                    showHelp(sender, page);
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid page number. Valid pages: 1-3");
                    return true;
                }
            } catch (NumberFormatException e) {
                // Not a number, treat as command name for detailed help
                showDetailedHelp(sender, args[1].toLowerCase());
                return true;
            }
        }

        // Delegate to specific commands based on first argument
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("zonerental.admin.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
                return true;

            case "list":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + getPrefix() + " list <player>");
                    return true;
                }
                // Handle list command
                return true;

            case "info":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + getPrefix() + " info <region>");
                    return true;
                }
                // Handle info command
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /" + getPrefix() + " help for help.");
                return true;
        }
    }
    
    private void showHelp(CommandSender sender, int page) {
        String prefix = getPrefix();
        sender.sendMessage(ChatColor.GOLD + "=== ZoneRental Help ===");
        sender.sendMessage("");

        if (page == 1) {
            // Page 1: User Commands
            sender.sendMessage(ChatColor.GOLD + "Player Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + " help" + ChatColor.GRAY + " - Show this help menu");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "info" + ChatColor.GRAY + " - View rental information for a region");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "list" + ChatColor.GRAY + " - List your active rentals");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "extend" + ChatColor.GRAY + " - Extend your rental duration");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "retrieve" + ChatColor.GRAY + " - Retrieve items from expired rentals");

        } else if (page == 2 && sender.hasPermission("zonerental.admin")) {
            // Page 2: Admin Commands Part 1
            sender.sendMessage(ChatColor.GOLD + "Admin Commands (Part 1):");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "reload" + ChatColor.GRAY + " - Reload plugin configuration");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "createsign" + ChatColor.GRAY + " - Create a rental sign for a region");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "reset" + ChatColor.GRAY + " - Reset a rental with full refund");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "remove" + ChatColor.GRAY + " - Completely remove rental setup from region");

        } else if (page == 3 && sender.hasPermission("zonerental.admin")) {
            // Page 3: Admin Commands Part 2
            sender.sendMessage(ChatColor.GOLD + "Admin Commands (Part 2):");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "duration" + ChatColor.GRAY + " - Modify rental duration (add/remove/set/reset)");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "override" + ChatColor.GRAY + " - Set per-region custom settings");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "verify" + ChatColor.GRAY + " - Verify region configurations");
            sender.sendMessage(ChatColor.YELLOW + "/" + prefix + "refundhistory" + ChatColor.GRAY + " - View refund transaction history");

        } else if ((page == 2 || page == 3) && !sender.hasPermission("zonerental.admin")) {
            // Non-admin trying to view admin pages
            sender.sendMessage(ChatColor.RED + "You don't have permission to view admin commands.");
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/" + prefix + " help 1" + ChatColor.GRAY + " to view player commands.");
            return;
        }

        // Footer with navigation instructions
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_AQUA + "Page " + page + "/3");
        sender.sendMessage(ChatColor.AQUA + "Usage: " + ChatColor.WHITE + "/" + prefix + " help [page]" + ChatColor.GRAY + " - View help page");
        sender.sendMessage(ChatColor.AQUA + "Usage: " + ChatColor.WHITE + "/" + prefix + " help <command>" + ChatColor.GRAY + " - Detailed command help");
        sender.sendMessage(ChatColor.GOLD + "========================");
    }

    private void showDetailedHelp(CommandSender sender, String commandName) {
        String prefix = getPrefix();
        sender.sendMessage(ChatColor.GOLD + "=== Detailed Command Help ===");
        sender.sendMessage("");

        switch (commandName) {
            case "help":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + " help");
                sender.sendMessage(ChatColor.GRAY + "Display help information for ZoneRental commands.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + " help [page]");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + " help <command>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.user");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + " help" + ChatColor.GRAY + " - Show page 1 of help");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + " help 2" + ChatColor.GRAY + " - Show page 2 of help");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + " help info" + ChatColor.GRAY + " - Show detailed help for /" + prefix + "info command");
                break;

            case "info":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "info");
                sender.sendMessage(ChatColor.GRAY + "View detailed information about a rental region including price, owner, and expiration time.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "info <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.info");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "info shop1" + ChatColor.GRAY + " - View info for region 'shop1'");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "info apartment_5" + ChatColor.GRAY + " - Check if apartment_5 is available");
                break;

            case "list":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "list");
                sender.sendMessage(ChatColor.GRAY + "List all your active rentals or view another player's rentals (admin only).");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "list [player]");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permissions:");
                sender.sendMessage(ChatColor.WHITE + "  zonerental.list " + ChatColor.GRAY + "- View your own rentals");
                sender.sendMessage(ChatColor.WHITE + "  zonerental.admin.list.others " + ChatColor.GRAY + "- View other players' rentals");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "list" + ChatColor.GRAY + " - List all your rentals");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "list Notch" + ChatColor.GRAY + " - List Notch's rentals (admin only)");
                break;

            case "extend":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "extend");
                sender.sendMessage(ChatColor.GRAY + "Extend the duration of your rental. Costs money and has a maximum extension limit.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "extend <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.extend");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "extend shop1" + ChatColor.GRAY + " - Extend your rental for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - You can only extend rentals you own");
                sender.sendMessage(ChatColor.GRAY + "  - Extensions have a cost (configurable per region)");
                sender.sendMessage(ChatColor.GRAY + "  - Maximum extension limit applies (check config)");
                break;

            case "retrieve":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "retrieve");
                sender.sendMessage(ChatColor.GRAY + "Open a GUI to retrieve items stored from your expired rentals.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "retrieve");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.retrieve");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "retrieve" + ChatColor.GRAY + " - Open item retrieval GUI");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Items are stored when your rental expires");
                sender.sendMessage(ChatColor.GRAY + "  - Only shows items from your expired rentals");
                break;

            case "reload":
                if (!sender.hasPermission("zonerental.admin.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "reload");
                sender.sendMessage(ChatColor.GRAY + "Reload all plugin configuration files without restarting the server.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "reload");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.reload");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "reload" + ChatColor.GRAY + " - Reload configuration");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Reloads config.yml, regions.yml, signs.yml, and storage.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Active rentals are not affected");
                break;

            case "createsign":
                if (!sender.hasPermission("zonerental.admin.createsign")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "createsign");
                sender.sendMessage(ChatColor.GRAY + "Create a rental sign on the block you're looking at.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "createsign <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.createsign");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "createsign shop1" + ChatColor.GRAY + " - Create rental sign for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Look at an existing sign block before running");
                sender.sendMessage(ChatColor.GRAY + "  - Sign will use default settings until overrides are set");
                sender.sendMessage(ChatColor.GRAY + "  - Support block is automatically protected");
                break;

            case "reset":
                if (!sender.hasPermission("zonerental.admin.reset")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "reset");
                sender.sendMessage(ChatColor.GRAY + "Reset an active rental with full refund. Keeps rental setup intact.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "reset <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.reset");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "reset shop1" + ChatColor.GRAY + " - Reset shop1 rental");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Player receives 100% refund (initial + all extensions)");
                sender.sendMessage(ChatColor.GRAY + "  - Player is removed from WorldGuard region");
                sender.sendMessage(ChatColor.GRAY + "  - Region blocks are restored to original state");
                sender.sendMessage(ChatColor.GRAY + "  - Sign, schematic, and configs remain (use /" + prefix + "remove to delete)");
                break;

            case "remove":
                if (!sender.hasPermission("zonerental.admin.remove")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "remove");
                sender.sendMessage(ChatColor.GRAY + "Completely remove ZoneRental setup from a region.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "remove <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.remove");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "remove shop1" + ChatColor.GRAY + " - Remove rental setup from shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Resets active rental with full refund");
                sender.sendMessage(ChatColor.GRAY + "  - Removes sign configuration and restores support block");
                sender.sendMessage(ChatColor.GRAY + "  - Deletes WorldEdit schematic");
                sender.sendMessage(ChatColor.GRAY + "  - Removes region from regions.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Use this to completely repurpose a region");
                break;

            case "duration":
                if (!sender.hasPermission("zonerental.admin.duration")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "duration");
                sender.sendMessage(ChatColor.GRAY + "Modify the duration of an active rental.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "duration add <region> <time> [--charge]");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "duration remove <region> <time>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "duration set <region> <time>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "duration reset <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.duration");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "duration add shop1 7d" + ChatColor.GRAY + " - Add 7 days (free)");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "duration add shop1 3d --charge" + ChatColor.GRAY + " - Add 3 days (charge player)");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "duration remove shop1 2d" + ChatColor.GRAY + " - Remove 2 days (auto-refund)");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "duration set shop1 14d" + ChatColor.GRAY + " - Set to exactly 14 days remaining");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "duration reset shop1" + ChatColor.GRAY + " - Reset to default duration");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Time Format:");
                sender.sendMessage(ChatColor.GRAY + "  Use 'd' for days, 'h' for hours, 'm' for minutes");
                sender.sendMessage(ChatColor.GRAY + "  Examples: 7d, 12h, 30m, 1d12h");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - 'reset' refunds extension costs if configured");
                sender.sendMessage(ChatColor.GRAY + "  - 'remove' provides automatic refund if configured");
                sender.sendMessage(ChatColor.GRAY + "  - '--charge' flag charges player for added time");
                break;

            case "override":
                if (!sender.hasPermission("zonerental.admin.override")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "override");
                sender.sendMessage(ChatColor.GRAY + "Set per-region custom settings (price, duration, extensions, etc.)");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override price <region> <amount>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override duration <region> <days>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override maxextensions <region> <count>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override extensionprice <region> <amount>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override allowextensions <region> <true|false>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override extensionduration <region> <days>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override remove <region>");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "override list [region]");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.override");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override price shop1 500" + ChatColor.GRAY + " - Set custom price");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override duration shop1 14" + ChatColor.GRAY + " - Set custom duration");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override maxextensions shop1 20" + ChatColor.GRAY + " - Set max extensions");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override extensionprice shop1 0" + ChatColor.GRAY + " - Auto-calculate price");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override allowextensions shop1 false" + ChatColor.GRAY + " - Disable extensions");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override remove shop1" + ChatColor.GRAY + " - Remove all overrides");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "override list shop1" + ChatColor.GRAY + " - View shop1 settings");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Overrides saved to regions.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Signs without overrides use config.yml defaults");
                sender.sendMessage(ChatColor.GRAY + "  - Sign updates automatically when override is set");
                break;

            case "verify":
                if (!sender.hasPermission("zonerental.admin.verify")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "verify");
                sender.sendMessage(ChatColor.GRAY + "Verify region configurations and check for orphaned configs.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "verify");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.verify");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "verify" + ChatColor.GRAY + " - Run configuration verification");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Reports regions using default vs custom settings");
                sender.sendMessage(ChatColor.GRAY + "  - Identifies orphaned configs (no sign exists)");
                sender.sendMessage(ChatColor.GRAY + "  - Helps maintain clean configuration");
                break;

            case "refundhistory":
                if (!sender.hasPermission("zonerental.admin.refundhistory")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/" + prefix + "refundhistory");
                sender.sendMessage(ChatColor.GRAY + "View all refund transactions for a rental.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /" + prefix + "refundhistory <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "zonerental.admin.refundhistory");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /" + prefix + "refundhistory shop1" + ChatColor.GRAY + " - View refund history for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Shows refund amounts, timestamps, and reasons");
                sender.sendMessage(ChatColor.GRAY + "  - Useful for tracking admin actions");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command: " + commandName);
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/" + prefix + " help" + ChatColor.GRAY + " to see available commands.");
                return;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "========================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "info", "list");
            
            if (sender.hasPermission("zonerental.admin")) {
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
