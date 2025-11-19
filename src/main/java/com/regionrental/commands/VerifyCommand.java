package com.regionrental.commands;

import com.regionrental.RegionRental;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class VerifyCommand implements CommandExecutor {

    private final RegionRental plugin;

    public VerifyCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("regionrental.admin.verify")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Check if verify command is enabled
        if (!plugin.getConfigManager().isEnableVerifyCommand()) {
            sender.sendMessage(ChatColor.RED + "The verify command is disabled in config.yml");
            sender.sendMessage(ChatColor.GRAY + "Enable it with: regions-config.enable-verify-command: true");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Region Configuration Verification ===");
        sender.sendMessage("");

        // Get verification report
        Map<String, Object> report = plugin.getRegionsConfig().getVerificationReport();

        int totalSigns = (int) report.get("totalSigns");
        int totalConfigs = (int) report.get("totalConfigs");
        List<String> missingConfigs = (List<String>) report.get("missingConfigs");
        List<String> orphanedConfigs = (List<String>) report.get("orphanedConfigs");

        int usingDefaults = missingConfigs.size();
        int withCustomSettings = totalSigns - usingDefaults;

        // Display summary
        sender.sendMessage(ChatColor.AQUA + "Summary:");
        sender.sendMessage(ChatColor.GRAY + "  Total regions with signs: " + ChatColor.WHITE + totalSigns);
        sender.sendMessage(ChatColor.GRAY + "  Using custom overrides:   " + ChatColor.WHITE + withCustomSettings);
        sender.sendMessage(ChatColor.GRAY + "  Using default settings:   " + ChatColor.WHITE + usingDefaults);
        sender.sendMessage("");

        // Display regions using defaults
        if (!missingConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Regions Using Defaults (" + missingConfigs.size() + "):");
            sender.sendMessage(ChatColor.GRAY + "  These regions use default values from config.yml:");
            for (String region : missingConfigs) {
                sender.sendMessage(ChatColor.GRAY + "    - " + ChatColor.WHITE + region);
            }
            sender.sendMessage(ChatColor.YELLOW + "  Use /rroverride to set custom values for these regions");
            sender.sendMessage("");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "All regions with signs have custom overrides configured!");
            sender.sendMessage("");
        }

        // Display orphaned configs (potential issue)
        if (!orphanedConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "Orphaned Configurations (" + orphanedConfigs.size() + "):");
            sender.sendMessage(ChatColor.GRAY + "  These custom configs exist but have no rental sign:");
            for (String region : orphanedConfigs) {
                sender.sendMessage(ChatColor.GRAY + "    - " + ChatColor.WHITE + region);
            }
            sender.sendMessage(ChatColor.YELLOW + "  Use /rroverride remove <region> to clean up unused configs");
            sender.sendMessage("");
        }

        // Summary message
        if (orphanedConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "✓ No issues found - all configs are linked to signs");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Found " + orphanedConfigs.size() + " orphaned config(s) - consider cleanup");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=================================");

        return true;
    }
}
