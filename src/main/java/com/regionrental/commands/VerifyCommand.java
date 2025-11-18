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

        // Get verification report BEFORE repair
        Map<String, Object> report = plugin.getRegionsConfig().getVerificationReport();

        int totalSigns = (int) report.get("totalSigns");
        int totalConfigs = (int) report.get("totalConfigs");
        List<String> missingConfigs = (List<String>) report.get("missingConfigs");
        List<String> orphanedConfigs = (List<String>) report.get("orphanedConfigs");

        // Display summary
        sender.sendMessage(ChatColor.AQUA + "Summary:");
        sender.sendMessage(ChatColor.GRAY + "  Regions with signs: " + ChatColor.WHITE + totalSigns);
        sender.sendMessage(ChatColor.GRAY + "  Regions configured: " + ChatColor.WHITE + totalConfigs);
        sender.sendMessage("");

        // Check for issues
        boolean hasIssues = !missingConfigs.isEmpty() || !orphanedConfigs.isEmpty();

        if (!hasIssues) {
            sender.sendMessage(ChatColor.GREEN + "✓ No issues found!");
            sender.sendMessage(ChatColor.GREEN + "  All regions with signs have configuration entries.");
            return true;
        }

        // Display missing configs
        if (!missingConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Missing Configurations (" + missingConfigs.size() + "):");
            sender.sendMessage(ChatColor.GRAY + "  These regions have signs but no config in regions.yml:");
            for (String region : missingConfigs) {
                sender.sendMessage(ChatColor.GRAY + "    - " + ChatColor.WHITE + region);
            }
            sender.sendMessage("");
        }

        // Display orphaned configs
        if (!orphanedConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "Orphaned Configurations (" + orphanedConfigs.size() + "):");
            sender.sendMessage(ChatColor.GRAY + "  These configs exist but have no rental sign:");
            for (String region : orphanedConfigs) {
                sender.sendMessage(ChatColor.GRAY + "    - " + ChatColor.WHITE + region);
            }
            sender.sendMessage(ChatColor.GRAY + "  (Can be manually removed or will be used when signs are created)");
            sender.sendMessage("");
        }

        // Perform repair
        if (!missingConfigs.isEmpty()) {
            sender.sendMessage(ChatColor.AQUA + "Running auto-repair...");
            plugin.getRegionsConfig().verifyAndRepairRegions();
            sender.sendMessage(ChatColor.GREEN + "✓ Added " + missingConfigs.size() + " missing configuration(s)");
            sender.sendMessage(ChatColor.GRAY + "  Check regions.yml to customize settings for these regions.");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=================================");

        return true;
    }
}
