package com.zonerental.commands

import com.zonerental.ZoneRental
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class ReloadCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("zonerental.admin.reload")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        plugin.reloadPlugin()
        sender.sendMessage(plugin.configManager.getMessage("config-reloaded"))
        return true
    }
}
