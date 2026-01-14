package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.asPlayerOrNull
import com.zonerental.extensions.sendMiniMessage
import org.bukkit.block.Sign
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CreateSignCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender.asPlayerOrNull() ?: run {
            sender.sendMiniMessage("<red>This command can only be used by players!")
            return true
        }

        if (!player.hasPermission("zonerental.admin.createsign")) {
            player.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            player.sendMiniMessage("<red>Usage: /zrcreatesign <region>")
            return true
        }

        val regionName = args[0]

        // Check if region exists in player's current world
        if (!plugin.worldGuardManager.regionExists(regionName, player.world)) {
            player.sendMessage(plugin.configManager.getMessage("region-not-found", "{region}", regionName))
            return true
        }

        // Get the block the player is looking at
        val targetBlock = player.getTargetBlock(null, 5)

        if (targetBlock.state !is Sign) {
            player.sendMiniMessage("<red>You must be looking at a sign!")
            return true
        }

        // Create the rental sign
        plugin.signManager.createSign(regionName, targetBlock.location)

        player.sendMessage(plugin.configManager.getMessage("sign-created", "{region}", regionName))
        player.sendMiniMessage("<gray>Use /zroverride to set custom rental settings for this region.")

        return true
    }
}
