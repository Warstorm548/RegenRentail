package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.asPlayerOrNull
import com.zonerental.extensions.sendMiniMessage
import com.zonerental.util.WorldRegionParser
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class ExtendCommand(private val plugin: ZoneRental) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender.asPlayerOrNull() ?: run {
            sender.sendMiniMessage("<red>This command can only be used by players!")
            return true
        }

        if (!player.hasPermission("zonerental.extend")) {
            player.sendMessage(plugin.configManager.getMessage("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            player.sendMiniMessage("<red>Usage: /zrextend <region>")
            player.sendMiniMessage("<yellow>Example: /zrextend shop1")
            return true
        }

        // Parse region argument with world inference (player's world)
        val parsed = WorldRegionParser.parse(args[0], player) ?: run {
            player.sendMiniMessage("<red>Invalid region format!")
            return true
        }

        val world = parsed.getWorld() ?: run {
            player.sendMiniMessage("<red>World not found!")
            return true
        }
        val regionName = parsed.regionName

        val rental = plugin.rentalManager.getRental(regionName, world)

        if (rental == null) {
            player.sendMiniMessage("<red>Region $regionName is not rented!")
            return true
        }

        // Check if player owns this rental
        if (rental.playerUUID != player.uniqueId) {
            player.sendMiniMessage("<red>You don't own this rental!")
            return true
        }

        // Check extension limit
        if (rental.extensionCount >= plugin.configManager.maxExtensions) {
            player.sendMessage(plugin.configManager.getMessage("max-extensions-reached"))
            return true
        }

        // Get extension price
        val basePrice = plugin.configManager.getPriceForRegion(regionName, world)
        val multiplier = plugin.config.getDouble("extension.price-multiplier", 1.0)
        val price = basePrice * multiplier

        // Check economy
        val economy = plugin.economy
        if (economy == null) {
            player.sendMiniMessage("<red>Economy system not available!")
            return true
        }

        if (!economy.has(player, price)) {
            val formattedPrice = String.format(plugin.configManager.currencyFormat, price)
            player.sendMessage(plugin.configManager.getMessage("not-enough-money", "{amount}", formattedPrice))
            return true
        }

        // Withdraw money
        economy.withdrawPlayer(player, price)

        // Extend rental
        val days = plugin.configManager.extensionDuration
        if (plugin.rentalManager.extendRental(regionName, world, player, days, price)) {
            val formattedPrice = String.format(plugin.configManager.currencyFormat, price)
            player.sendMessage(plugin.configManager.getMessage("rental-extended",
                "{region}", regionName,
                "{days}", days.toString(),
                "{price}", formattedPrice))
        } else {
            // Refund if extension failed
            economy.depositPlayer(player, price)
            player.sendMiniMessage("<red>Failed to extend rental!")
        }

        return true
    }
}
