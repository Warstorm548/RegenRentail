package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.sendMiniMessage
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
            sender.sendMiniMessage("<red>The verify command is disabled in config.yml")
            sender.sendMiniMessage("<gray>Enable it with: regions-config.enable-verify-command: true")
            return true
        }

        sender.sendMiniMessage("<gold><bold>=== Region Configuration Verification ===")
        sender.sendMiniMessage("")

        // Get verification report with safe casts
        val report = plugin.regionsConfig.getVerificationReport()
        val totalSigns = report["totalSigns"] as? Int ?: 0
        val missingConfigs = (report["missingConfigs"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val orphanedConfigs = (report["orphanedConfigs"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val usingDefaults = missingConfigs.size
        val withCustomSettings = totalSigns - usingDefaults

        // Display summary
        sender.sendMiniMessage("<aqua>Summary:")
        sender.sendMiniMessage("<gray>  Total regions with signs: <white>$totalSigns")
        sender.sendMiniMessage("<gray>  Using custom overrides:   <white>$withCustomSettings")
        sender.sendMiniMessage("<gray>  Using default settings:   <white>$usingDefaults")
        sender.sendMiniMessage("")

        // Display regions using defaults
        if (missingConfigs.isNotEmpty()) {
            sender.sendMiniMessage("<green>Regions Using Defaults (${missingConfigs.size}):")
            sender.sendMiniMessage("<gray>  These regions use default values from config.yml:")
            missingConfigs.forEach { region ->
                sender.sendMiniMessage("<gray>    - <white>$region")
            }
            sender.sendMiniMessage("<yellow>  Use /zroverride to set custom values for these regions")
            sender.sendMiniMessage("")
        } else {
            sender.sendMiniMessage("<yellow>All regions with signs have custom overrides configured!")
            sender.sendMiniMessage("")
        }

        // Display orphaned configs (potential issue)
        if (orphanedConfigs.isNotEmpty()) {
            sender.sendMiniMessage("<gold>Orphaned Configurations (${orphanedConfigs.size}):")
            sender.sendMiniMessage("<gray>  These custom configs exist but have no rental sign:")
            orphanedConfigs.forEach { region ->
                sender.sendMiniMessage("<gray>    - <white>$region")
            }
            sender.sendMiniMessage("<yellow>  Use /zroverride remove <region> to clean up unused configs")
            sender.sendMiniMessage("")
        }

        // Summary message
        if (orphanedConfigs.isEmpty()) {
            sender.sendMiniMessage("<green>✓ No issues found - all configs are linked to signs")
        } else {
            sender.sendMiniMessage("<yellow>⚠ Found ${orphanedConfigs.size} orphaned config(s) - consider cleanup")
        }

        sender.sendMiniMessage("")
        sender.sendMiniMessage("<gold><bold>=================================")

        return true
    }
}
