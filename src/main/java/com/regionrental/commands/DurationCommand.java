package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.managers.Rental;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationCommand implements CommandExecutor, TabCompleter {
    
    private final RegionRental plugin;
    private final Pattern timePattern = Pattern.compile("(\\d+)\\s*(days?|hours?|hrs?|minutes?|mins?)");
    
    public DurationCommand(RegionRental plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("regionrental.admin.duration")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        
        // Usage: /rrduration <add|remove|set|reset> <region> [<time>]
        // Example: /rrduration add shop1 2 days 3 hours 30 minutes
        // Example: /rrduration remove shop1 1 hour 30 mins
        // Example: /rrduration set shop1 7 days
        // Example: /rrduration reset shop1
        
        if (args.length < 2) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        String regionName = args[1];

        // Check if region exists and is rented
        Rental rental = plugin.getRentalManager().getRental(regionName);
        if (rental == null) {
            sender.sendMessage(ChatColor.RED + "Region " + regionName + " is not currently rented!");
            return true;
        }

        // Handle reset action (doesn't require time parameter)
        if (action.equals("reset")) {
            resetDuration(sender, rental);
            return true;
        }

        // For other actions, we need at least 3 arguments
        if (args.length < 3) {
            showUsage(sender);
            return true;
        }

        // Parse the time from remaining arguments
        StringBuilder timeString = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            timeString.append(args[i]).append(" ");
        }

        long millisToModify = parseTimeString(timeString.toString().trim());

        if (millisToModify <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid time format! Use: <number> <days|hours|minutes>");
            sender.sendMessage(ChatColor.YELLOW + "Example: 2 days 3 hours 30 minutes");
            return true;
        }

        // Apply the modification
        switch (action) {
            case "add":
                addDuration(sender, rental, millisToModify);
                break;
            case "remove":
                removeDuration(sender, rental, millisToModify);
                break;
            case "set":
                setDuration(sender, rental, millisToModify);
                break;
            default:
                showUsage(sender);
                return true;
        }

        return true;
    }
    
    private void addDuration(CommandSender sender, Rental rental, long millis) {
        long newEndDate = rental.getEndDate() + millis;
        rental.setEndDate(newEndDate);
        
        // Update sign
        plugin.getSignManager().updateSign(rental.getRegionName());
        
        // Save
        plugin.getRentalManager().saveAllRentals();
        
        String timeAdded = formatMillis(millis);
        sender.sendMessage(ChatColor.GREEN + "Added " + timeAdded + " to rental " + rental.getRegionName());
        sender.sendMessage(ChatColor.YELLOW + "New expiration: " + rental.getFormattedEndDate());
        
        // Log the action
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(sender.getName() + " added " + timeAdded + " to rental " + rental.getRegionName());
        }
    }
    
    private void removeDuration(CommandSender sender, Rental rental, long millis) {
        long currentTime = System.currentTimeMillis();
        long newEndDate = rental.getEndDate() - millis;
        
        // Don't allow setting time in the past
        if (newEndDate <= currentTime) {
            sender.sendMessage(ChatColor.RED + "Cannot remove that much time - would expire the rental!");
            sender.sendMessage(ChatColor.YELLOW + "Time remaining: " + formatMillis(rental.getTimeRemaining()));
            return;
        }
        
        rental.setEndDate(newEndDate);
        
        // Update sign
        plugin.getSignManager().updateSign(rental.getRegionName());
        
        // Save
        plugin.getRentalManager().saveAllRentals();
        
        String timeRemoved = formatMillis(millis);
        sender.sendMessage(ChatColor.GREEN + "Removed " + timeRemoved + " from rental " + rental.getRegionName());
        sender.sendMessage(ChatColor.YELLOW + "New expiration: " + rental.getFormattedEndDate());
        
        // Log the action
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(sender.getName() + " removed " + timeRemoved + " from rental " + rental.getRegionName());
        }
    }
    
    private void setDuration(CommandSender sender, Rental rental, long millis) {
        long newEndDate = System.currentTimeMillis() + millis;
        rental.setEndDate(newEndDate);
        
        // Update sign
        plugin.getSignManager().updateSign(rental.getRegionName());
        
        // Save
        plugin.getRentalManager().saveAllRentals();
        
        String newDuration = formatMillis(millis);
        sender.sendMessage(ChatColor.GREEN + "Set rental duration for " + rental.getRegionName() + " to " + newDuration);
        sender.sendMessage(ChatColor.YELLOW + "New expiration: " + rental.getFormattedEndDate());
        
        // Log the action
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(sender.getName() + " set duration of " + rental.getRegionName() + " to " + newDuration);
        }
    }

    private void resetDuration(CommandSender sender, Rental rental) {
        // Get default duration from config
        int defaultDays = plugin.getConfigManager().getDefaultDuration();
        long defaultMillis = defaultDays * 24L * 60L * 60L * 1000L;

        // Check if extension refund is enabled
        double refundAmount = 0.0;
        if (plugin.getConfigManager().isRefundOnDurationReset()) {
            refundAmount = rental.getExtensionCost();
        }

        // Refund extension money if applicable
        if (refundAmount > 0 && plugin.getEconomy() != null) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(rental.getPlayerUUID());
            plugin.getEconomy().depositPlayer(player, refundAmount);

            String formattedAmount = String.format(plugin.getConfigManager().getCurrencyFormat(), refundAmount);

            // Notify player if online
            if (player.isOnline()) {
                ((Player) player).sendMessage(ChatColor.GREEN + "Your extension costs for " +
                    ChatColor.YELLOW + rental.getRegionName() +
                    ChatColor.GREEN + " have been refunded: " + ChatColor.GOLD + formattedAmount);
            }

            sender.sendMessage(ChatColor.GREEN + "Refunded extension costs: " + ChatColor.GOLD + formattedAmount);
        }

        // Reset time to default duration
        rental.resetTime(defaultDays);

        // Update sign
        plugin.getSignManager().updateSign(rental.getRegionName());

        // Save
        plugin.getRentalManager().saveAllRentals();

        // Notify sender
        sender.sendMessage(ChatColor.GREEN + "Reset duration for " + ChatColor.YELLOW + rental.getRegionName() +
            ChatColor.GREEN + " to default: " + ChatColor.GOLD + defaultDays + " days");
        sender.sendMessage(ChatColor.YELLOW + "New expiration: " + rental.getFormattedEndDate());

        // Log the action
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(sender.getName() + " reset duration of " + rental.getRegionName() +
                " to default (" + defaultDays + " days)" +
                (refundAmount > 0 ? " with refund: " + refundAmount : ""));
        }
    }

    private long parseTimeString(String timeStr) {
        long totalMillis = 0;
        
        // Convert common variations to standard format
        timeStr = timeStr.toLowerCase()
            .replaceAll("(\\d+)d\\b", "$1 days")
            .replaceAll("(\\d+)h\\b", "$1 hours")
            .replaceAll("(\\d+)m\\b", "$1 minutes")
            .replaceAll("(\\d+)s\\b", "$1 seconds");
        
        Matcher matcher = timePattern.matcher(timeStr);
        
        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            
            if (unit.startsWith("day")) {
                totalMillis += amount * 24L * 60L * 60L * 1000L;
            } else if (unit.startsWith("hour") || unit.startsWith("hr")) {
                totalMillis += amount * 60L * 60L * 1000L;
            } else if (unit.startsWith("minute") || unit.startsWith("min")) {
                totalMillis += amount * 60L * 1000L;
            } else if (unit.startsWith("second") || unit.startsWith("sec")) {
                totalMillis += amount * 1000L;
            }
        }
        
        return totalMillis;
    }
    
    private String formatMillis(long millis) {
        long days = millis / (24L * 60L * 60L * 1000L);
        millis %= (24L * 60L * 60L * 1000L);
        
        long hours = millis / (60L * 60L * 1000L);
        millis %= (60L * 60L * 1000L);
        
        long minutes = millis / (60L * 1000L);
        
        StringBuilder result = new StringBuilder();
        
        if (days > 0) {
            result.append(days).append(" day").append(days > 1 ? "s" : "").append(" ");
        }
        if (hours > 0) {
            result.append(hours).append(" hour").append(hours > 1 ? "s" : "").append(" ");
        }
        if (minutes > 0) {
            result.append(minutes).append(" minute").append(minutes > 1 ? "s" : "");
        }
        
        return result.toString().trim();
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Duration Command Usage ===");
        sender.sendMessage(ChatColor.YELLOW + "/rrduration add <region> <time>");
        sender.sendMessage(ChatColor.GRAY + "  Example: /rrduration add shop1 2 days 3 hours 30 minutes");
        sender.sendMessage(ChatColor.YELLOW + "/rrduration remove <region> <time>");
        sender.sendMessage(ChatColor.GRAY + "  Example: /rrduration remove shop1 1 hour 30 mins");
        sender.sendMessage(ChatColor.YELLOW + "/rrduration set <region> <time>");
        sender.sendMessage(ChatColor.GRAY + "  Example: /rrduration set shop1 7 days");
        sender.sendMessage(ChatColor.YELLOW + "/rrduration reset <region>");
        sender.sendMessage(ChatColor.GRAY + "  Example: /rrduration reset shop1");
        sender.sendMessage(ChatColor.GRAY + "  Resets to default duration, refunds extensions if configured");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Time formats: days, hours, minutes");
        sender.sendMessage(ChatColor.AQUA + "Short forms: d, h, m (e.g., 2d 3h 30m)");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("regionrental.admin.duration")) {
            return completions;
        }
        
        if (args.length == 1) {
            // First argument: action
            List<String> actions = Arrays.asList("add", "remove", "set", "reset");
            String partial = args[0].toLowerCase();
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            // Second argument: region name (show rented regions)
            String partial = args[1].toLowerCase();
            for (Rental rental : plugin.getRentalManager().getAllRentals()) {
                if (rental.getRegionName().toLowerCase().startsWith(partial)) {
                    completions.add(rental.getRegionName());
                }
            }
        } else if (args.length >= 3) {
            // Time suggestions
            if (args[args.length - 1].matches("\\d+")) {
                completions.addAll(Arrays.asList("days", "hours", "minutes"));
            } else {
                completions.addAll(Arrays.asList("1", "2", "3", "5", "7", "10", "30"));
            }
        }
        
        return completions;
    }
}
