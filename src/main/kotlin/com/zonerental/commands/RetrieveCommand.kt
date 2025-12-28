package com.zonerental.commands

import com.zonerental.ZoneRental
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RetrieveCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return true
        }

        if (!sender.hasPermission("zonerental.retrieve")) {
            sender.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        // Open the retrieval GUI
        plugin.storageManager.openRetrievalGUI(sender)

        return true
    }
}
