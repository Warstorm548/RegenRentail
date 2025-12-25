package com.zonerental.commands

import com.zonerental.ZoneRental
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command to verify region configuration integrity.
 * Shows regions using defaults and orphaned configurations.
 */
class VerifyCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.admin.verify")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (!plugin.configManager.isEnableVerifyCommand) {
            sender.sendMessage("${ChatColor.RED}The verify command is disabled in config.yml")
            sender.sendMessage("${ChatColor.GRAY}Enable it with: regions-config.enable-verify-command: true")
            return true
        }

        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Region Configuration Verification ===")
        sender.sendMessage("")

        // Get verification report with safe casts
        val report = plugin.regionsConfig.getVerificationReport()
        val totalSigns = report["totalSigns"] as? Int ?: 0
        val missingConfigs = (report["missingConfigs"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val orphanedConfigs = (report["orphanedConfigs"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val usingDefaults = missingConfigs.size
        val withCustomSettings = totalSigns - usingDefaults

        // Display summary
        sender.sendMessage("${ChatColor.AQUA}Summary:")
        sender.sendMessage("${ChatColor.GRAY}  Total regions with signs: ${ChatColor.WHITE}$totalSigns")
        sender.sendMessage("${ChatColor.GRAY}  Using custom overrides:   ${ChatColor.WHITE}$withCustomSettings")
        sender.sendMessage("${ChatColor.GRAY}  Using default settings:   ${ChatColor.WHITE}$usingDefaults")
        sender.sendMessage("")

        // Display regions using defaults
        if (missingConfigs.isNotEmpty()) {
            sender.sendMessage("${ChatColor.GREEN}Regions Using Defaults (${missingConfigs.size}):")
            sender.sendMessage("${ChatColor.GRAY}  These regions use default values from config.yml:")
            missingConfigs.forEach { region ->
                sender.sendMessage("${ChatColor.GRAY}    - ${ChatColor.WHITE}$region")
            }
            sender.sendMessage("${ChatColor.YELLOW}  Use /zroverride to set custom values for these regions")
            sender.sendMessage("")
        } else {
            sender.sendMessage("${ChatColor.YELLOW}All regions with signs have custom overrides configured!")
            sender.sendMessage("")
        }

        // Display orphaned configs (potential issue)
        if (orphanedConfigs.isNotEmpty()) {
            sender.sendMessage("${ChatColor.GOLD}Orphaned Configurations (${orphanedConfigs.size}):")
            sender.sendMessage("${ChatColor.GRAY}  These custom configs exist but have no rental sign:")
            orphanedConfigs.forEach { region ->
                sender.sendMessage("${ChatColor.GRAY}    - ${ChatColor.WHITE}$region")
            }
            sender.sendMessage("${ChatColor.YELLOW}  Use /zroverride remove <region> to clean up unused configs")
            sender.sendMessage("")
        }

        // Summary message
        if (orphanedConfigs.isEmpty()) {
            sender.sendMessage("${ChatColor.GREEN}✓ No issues found - all configs are linked to signs")
        } else {
            sender.sendMessage("${ChatColor.YELLOW}⚠ Found ${orphanedConfigs.size} orphaned config(s) - consider cleanup")
        }

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=================================")

        return true
    }
}
