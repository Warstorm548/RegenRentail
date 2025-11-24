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
    
    private void showHelp(CommandSender sender, int page) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionRental Help ===");
        sender.sendMessage("");

        if (page == 1) {
            // Page 1: User Commands
            sender.sendMessage(ChatColor.GOLD + "Player Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/rr help" + ChatColor.GRAY + " - Show this help menu");
            sender.sendMessage(ChatColor.YELLOW + "/rrinfo" + ChatColor.GRAY + " - View rental information for a region");
            sender.sendMessage(ChatColor.YELLOW + "/rrlist" + ChatColor.GRAY + " - List your active rentals");
            sender.sendMessage(ChatColor.YELLOW + "/rrextend" + ChatColor.GRAY + " - Extend your rental duration");
            sender.sendMessage(ChatColor.YELLOW + "/rrretrieve" + ChatColor.GRAY + " - Retrieve items from expired rentals");

        } else if (page == 2 && sender.hasPermission("regionrental.admin")) {
            // Page 2: Admin Commands Part 1
            sender.sendMessage(ChatColor.GOLD + "Admin Commands (Part 1):");
            sender.sendMessage(ChatColor.YELLOW + "/rrreload" + ChatColor.GRAY + " - Reload plugin configuration");
            sender.sendMessage(ChatColor.YELLOW + "/rrcreatesign" + ChatColor.GRAY + " - Create a rental sign for a region");
            sender.sendMessage(ChatColor.YELLOW + "/rrreset" + ChatColor.GRAY + " - Reset a rental with full refund");
            sender.sendMessage(ChatColor.YELLOW + "/rrremove" + ChatColor.GRAY + " - Completely remove rental setup from region");

        } else if (page == 3 && sender.hasPermission("regionrental.admin")) {
            // Page 3: Admin Commands Part 2
            sender.sendMessage(ChatColor.GOLD + "Admin Commands (Part 2):");
            sender.sendMessage(ChatColor.YELLOW + "/rrduration" + ChatColor.GRAY + " - Modify rental duration (add/remove/set/reset)");
            sender.sendMessage(ChatColor.YELLOW + "/rroverride" + ChatColor.GRAY + " - Set per-region custom settings");
            sender.sendMessage(ChatColor.YELLOW + "/rrverify" + ChatColor.GRAY + " - Verify region configurations");
            sender.sendMessage(ChatColor.YELLOW + "/rrrefundhistory" + ChatColor.GRAY + " - View refund transaction history");

        } else if ((page == 2 || page == 3) && !sender.hasPermission("regionrental.admin")) {
            // Non-admin trying to view admin pages
            sender.sendMessage(ChatColor.RED + "You don't have permission to view admin commands.");
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/rr help 1" + ChatColor.GRAY + " to view player commands.");
            return;
        }

        // Footer with navigation instructions
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_AQUA + "Page " + page + "/3");
        sender.sendMessage(ChatColor.AQUA + "Usage: " + ChatColor.WHITE + "/rr help [page]" + ChatColor.GRAY + " - View help page");
        sender.sendMessage(ChatColor.AQUA + "Usage: " + ChatColor.WHITE + "/rr help <command>" + ChatColor.GRAY + " - Detailed command help");
        sender.sendMessage(ChatColor.GOLD + "========================");
    }

    private void showDetailedHelp(CommandSender sender, String commandName) {
        sender.sendMessage(ChatColor.GOLD + "=== Detailed Command Help ===");
        sender.sendMessage("");

        switch (commandName) {
            case "help":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rr help");
                sender.sendMessage(ChatColor.GRAY + "Display help information for RegionRental commands.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rr help [page]");
                sender.sendMessage(ChatColor.WHITE + "  /rr help <command>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.user");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rr help" + ChatColor.GRAY + " - Show page 1 of help");
                sender.sendMessage(ChatColor.GREEN + "  /rr help 2" + ChatColor.GRAY + " - Show page 2 of help");
                sender.sendMessage(ChatColor.GREEN + "  /rr help info" + ChatColor.GRAY + " - Show detailed help for /rrinfo command");
                break;

            case "info":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrinfo");
                sender.sendMessage(ChatColor.GRAY + "View detailed information about a rental region including price, owner, and expiration time.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrinfo <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.info");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrinfo shop1" + ChatColor.GRAY + " - View info for region 'shop1'");
                sender.sendMessage(ChatColor.GREEN + "  /rrinfo apartment_5" + ChatColor.GRAY + " - Check if apartment_5 is available");
                break;

            case "list":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrlist");
                sender.sendMessage(ChatColor.GRAY + "List all your active rentals or view another player's rentals (admin only).");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrlist [player]");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permissions:");
                sender.sendMessage(ChatColor.WHITE + "  regionrental.list " + ChatColor.GRAY + "- View your own rentals");
                sender.sendMessage(ChatColor.WHITE + "  regionrental.admin.list.others " + ChatColor.GRAY + "- View other players' rentals");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrlist" + ChatColor.GRAY + " - List all your rentals");
                sender.sendMessage(ChatColor.GREEN + "  /rrlist Notch" + ChatColor.GRAY + " - List Notch's rentals (admin only)");
                break;

            case "extend":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrextend");
                sender.sendMessage(ChatColor.GRAY + "Extend the duration of your rental. Costs money and has a maximum extension limit.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrextend <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.extend");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrextend shop1" + ChatColor.GRAY + " - Extend your rental for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - You can only extend rentals you own");
                sender.sendMessage(ChatColor.GRAY + "  - Extensions have a cost (configurable per region)");
                sender.sendMessage(ChatColor.GRAY + "  - Maximum extension limit applies (check config)");
                break;

            case "retrieve":
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrretrieve");
                sender.sendMessage(ChatColor.GRAY + "Open a GUI to retrieve items stored from your expired rentals.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrretrieve");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.retrieve");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrretrieve" + ChatColor.GRAY + " - Open item retrieval GUI");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Items are stored when your rental expires");
                sender.sendMessage(ChatColor.GRAY + "  - Only shows items from your expired rentals");
                break;

            case "reload":
                if (!sender.hasPermission("regionrental.admin.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrreload");
                sender.sendMessage(ChatColor.GRAY + "Reload all plugin configuration files without restarting the server.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrreload");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.reload");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrreload" + ChatColor.GRAY + " - Reload configuration");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Reloads config.yml, regions.yml, signs.yml, and storage.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Active rentals are not affected");
                break;

            case "createsign":
                if (!sender.hasPermission("regionrental.admin.createsign")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrcreatesign");
                sender.sendMessage(ChatColor.GRAY + "Create a rental sign on the block you're looking at.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrcreatesign <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.createsign");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrcreatesign shop1" + ChatColor.GRAY + " - Create rental sign for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Look at an existing sign block before running");
                sender.sendMessage(ChatColor.GRAY + "  - Sign will use default settings until overrides are set");
                sender.sendMessage(ChatColor.GRAY + "  - Support block is automatically protected");
                break;

            case "reset":
                if (!sender.hasPermission("regionrental.admin.reset")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrreset");
                sender.sendMessage(ChatColor.GRAY + "Reset an active rental with full refund. Keeps rental setup intact.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrreset <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.reset");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrreset shop1" + ChatColor.GRAY + " - Reset shop1 rental");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Player receives 100% refund (initial + all extensions)");
                sender.sendMessage(ChatColor.GRAY + "  - Player is removed from WorldGuard region");
                sender.sendMessage(ChatColor.GRAY + "  - Region blocks are restored to original state");
                sender.sendMessage(ChatColor.GRAY + "  - Sign, schematic, and configs remain (use /rrremove to delete)");
                break;

            case "remove":
                if (!sender.hasPermission("regionrental.admin.remove")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrremove");
                sender.sendMessage(ChatColor.GRAY + "Completely remove RegionRental setup from a region.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrremove <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.remove");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrremove shop1" + ChatColor.GRAY + " - Remove rental setup from shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Resets active rental with full refund");
                sender.sendMessage(ChatColor.GRAY + "  - Removes sign configuration and restores support block");
                sender.sendMessage(ChatColor.GRAY + "  - Deletes WorldEdit schematic");
                sender.sendMessage(ChatColor.GRAY + "  - Removes region from regions.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Use this to completely repurpose a region");
                break;

            case "duration":
                if (!sender.hasPermission("regionrental.admin.duration")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrduration");
                sender.sendMessage(ChatColor.GRAY + "Modify the duration of an active rental.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrduration add <region> <time> [--charge]");
                sender.sendMessage(ChatColor.WHITE + "  /rrduration remove <region> <time>");
                sender.sendMessage(ChatColor.WHITE + "  /rrduration set <region> <time>");
                sender.sendMessage(ChatColor.WHITE + "  /rrduration reset <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.duration");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrduration add shop1 7d" + ChatColor.GRAY + " - Add 7 days (free)");
                sender.sendMessage(ChatColor.GREEN + "  /rrduration add shop1 3d --charge" + ChatColor.GRAY + " - Add 3 days (charge player)");
                sender.sendMessage(ChatColor.GREEN + "  /rrduration remove shop1 2d" + ChatColor.GRAY + " - Remove 2 days (auto-refund)");
                sender.sendMessage(ChatColor.GREEN + "  /rrduration set shop1 14d" + ChatColor.GRAY + " - Set to exactly 14 days remaining");
                sender.sendMessage(ChatColor.GREEN + "  /rrduration reset shop1" + ChatColor.GRAY + " - Reset to default duration");
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
                if (!sender.hasPermission("regionrental.admin.override")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rroverride");
                sender.sendMessage(ChatColor.GRAY + "Set per-region custom settings (price, duration, extensions, etc.)");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride price <region> <amount>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride duration <region> <days>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride maxextensions <region> <count>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride extensionprice <region> <amount>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride allowextensions <region> <true|false>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride extensionduration <region> <days>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride remove <region>");
                sender.sendMessage(ChatColor.WHITE + "  /rroverride list [region]");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.override");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride price shop1 500" + ChatColor.GRAY + " - Set custom price");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride duration shop1 14" + ChatColor.GRAY + " - Set custom duration");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride maxextensions shop1 20" + ChatColor.GRAY + " - Set max extensions");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride extensionprice shop1 0" + ChatColor.GRAY + " - Auto-calculate price");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride allowextensions shop1 false" + ChatColor.GRAY + " - Disable extensions");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride remove shop1" + ChatColor.GRAY + " - Remove all overrides");
                sender.sendMessage(ChatColor.GREEN + "  /rroverride list shop1" + ChatColor.GRAY + " - View shop1 settings");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Overrides saved to regions.yml");
                sender.sendMessage(ChatColor.GRAY + "  - Signs without overrides use config.yml defaults");
                sender.sendMessage(ChatColor.GRAY + "  - Sign updates automatically when override is set");
                break;

            case "verify":
                if (!sender.hasPermission("regionrental.admin.verify")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrverify");
                sender.sendMessage(ChatColor.GRAY + "Verify region configurations and check for orphaned configs.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrverify");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.verify");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrverify" + ChatColor.GRAY + " - Run configuration verification");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Reports regions using default vs custom settings");
                sender.sendMessage(ChatColor.GRAY + "  - Identifies orphaned configs (no sign exists)");
                sender.sendMessage(ChatColor.GRAY + "  - Helps maintain clean configuration");
                break;

            case "refundhistory":
                if (!sender.hasPermission("regionrental.admin.refundhistory")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view this command's help.");
                    return;
                }
                sender.sendMessage(ChatColor.YELLOW + "Command: " + ChatColor.WHITE + "/rrrefundhistory");
                sender.sendMessage(ChatColor.GRAY + "View all refund transactions for a rental.");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Syntax:");
                sender.sendMessage(ChatColor.WHITE + "  /rrrefundhistory <region>");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Permission: " + ChatColor.WHITE + "regionrental.admin.refundhistory");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Examples:");
                sender.sendMessage(ChatColor.GREEN + "  /rrrefundhistory shop1" + ChatColor.GRAY + " - View refund history for shop1");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "Notes:");
                sender.sendMessage(ChatColor.GRAY + "  - Shows refund amounts, timestamps, and reasons");
                sender.sendMessage(ChatColor.GRAY + "  - Useful for tracking admin actions");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command: " + commandName);
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/rr help" + ChatColor.GRAY + " to see available commands.");
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
