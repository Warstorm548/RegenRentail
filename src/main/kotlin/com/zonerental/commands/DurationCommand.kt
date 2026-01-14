package com.zonerental.commands

import com.zonerental.ZoneRental
import com.zonerental.extensions.checkPermission
import com.zonerental.extensions.sendMiniMessage
import com.zonerental.managers.Rental
import com.zonerental.util.TimeUtils
import com.zonerental.util.WorldRegionParser
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.regex.Pattern

/**
 * Command for modifying rental durations.
 * Usage: /zrduration <add|remove|set|reset> <region> [time] [--charge]
 */
class DurationCommand(private val plugin: ZoneRental) : CommandExecutor, TabCompleter {

    companion object {
        private val ACTIONS = listOf("add", "remove", "set", "reset")
        private val TIME_PATTERN = Pattern.compile("(\\d+)\\s*(days?|hours?|hrs?|minutes?|mins?)")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        sender.checkPermission("zonerental.admin.duration", plugin.configManager.getMessage("no-permission"))
            ?: return true

        if (args.size < 2) {
            showUsage(sender)
            return true
        }

        val action = args[0].lowercase()

        // Parse region with world inference
        val parsed = WorldRegionParser.parse(args[1], sender) ?: run {
            sender.sendMiniMessage("<red>Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld() ?: run {
            sender.sendMiniMessage("<red>World '${parsed.worldName}' is not loaded!")
            return true
        }
        val regionName = parsed.regionName

        // Check if region is rented
        val rental = plugin.rentalManager.getRental(regionName, world) ?: run {
            sender.sendMiniMessage("<red>Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        // Handle reset action (no time parameter needed)
        if (action == "reset") {
            handleReset(sender, rental, world)
            return true
        }

        // Other actions require time parameter
        if (args.size < 3) {
            sender.sendMiniMessage("<red>Usage: /zrduration $action <region> <time>")
            sender.sendMiniMessage("<gray>Example: /zrduration $action shop1 7d")
            return true
        }

        // Parse --charge flag and time arguments
        val chargePlayer = args.any { it.equals("--charge", ignoreCase = true) }
        val timeArgs = args.drop(2).filterNot { it.equals("--charge", ignoreCase = true) }

        if (timeArgs.isEmpty()) {
            sender.sendMiniMessage("<red>No time specified!")
            showUsage(sender)
            return true
        }

        val timeString = timeArgs.joinToString(" ")
        val millisToModify = parseTimeString(timeString)

        if (millisToModify <= 0) {
            sender.sendMiniMessage("<red>Invalid time format! Use: <number> <days|hours|minutes>")
            sender.sendMiniMessage("<yellow>Example: 2 days 3 hours 30 minutes")
            return true
        }

        // Execute the action
        when (action) {
            "add" -> handleAdd(sender, rental, world, millisToModify, chargePlayer)
            "remove" -> handleRemove(sender, rental, world, millisToModify)
            "set" -> handleSet(sender, rental, world, millisToModify)
            else -> showUsage(sender)
        }

        return true
    }

    private fun handleAdd(sender: CommandSender, rental: Rental, world: World, millis: Long, chargePlayer: Boolean) {
        val timeAdded = TimeUtils.formatDuration(millis)
        val days = TimeUtils.millisToDays(millis)

        if (chargePlayer && plugin.configManager.isChargeForDurationAdd) {
            val player = plugin.server.getPlayer(rental.playerUUID)

            if (player == null || !player.isOnline) {
                sender.sendMiniMessage("<red>Player must be online to charge for time addition!")
                return
            }

            // Attempt to charge the player
            val charged = plugin.rentalManager.chargeDurationAdd(rental, days, player)

            if (!charged) {
                sender.sendMiniMessage("<red>Failed to charge player! They may not have enough money.")
                return
            }

            // Get cost for feedback
            val extensionPrice = plugin.configManager.getExtensionPrice(rental.regionName, world)
            val totalCost = extensionPrice * days
            val formattedAmount = String.format(plugin.configManager.currencyFormat, totalCost)

            sender.sendMessage(plugin.configManager.getMessage("duration-add-charged",
                "{days}", days.toString(),
                "{region}", rental.regionName,
                "{player}", rental.playerName,
                "{amount}", formattedAmount))

            sender.sendMiniMessage("<yellow>New expiration: ${rental.formattedEndDate}")

            // Notify player
            player.sendMiniMessage("<green>Admin ${sender.name} added $timeAdded to your rental of <yellow>${rental.regionName}")
            player.sendMiniMessage("<green>You were charged: <gold>$formattedAmount")

            plugin.logger.info("${sender.name} added $timeAdded to rental ${rental.regionName} (charged $formattedAmount)")
        } else {
            // Free addition
            rental.endDate = rental.endDate + millis

            plugin.signManager.updateSign(rental.regionName, world)
            plugin.rentalManager.saveAllRentals()

            sender.sendMessage(plugin.configManager.getMessage("duration-add-free",
                "{days}", timeAdded,
                "{region}", rental.regionName))

            sender.sendMiniMessage("<yellow>New expiration: ${rental.formattedEndDate}")

            plugin.logger.info("${sender.name} added $timeAdded to rental ${rental.regionName} (free)")
        }
    }

    private fun handleRemove(sender: CommandSender, rental: Rental, world: World, millis: Long) {
        val currentTime = System.currentTimeMillis()
        val newEndDate = rental.endDate - millis

        // Don't allow setting time in the past
        if (newEndDate <= currentTime) {
            sender.sendMiniMessage("<red>Cannot remove that much time - would expire the rental!")
            sender.sendMiniMessage("<yellow>Time remaining: ${TimeUtils.formatDuration(rental.timeRemaining)}")
            return
        }

        val timeRemoved = TimeUtils.formatDuration(millis)
        val daysRemoved = TimeUtils.millisToDays(millis)

        // Calculate and issue proportional refund if enabled
        var refundAmount = 0.0
        if (plugin.configManager.isRefundOnTimeRemoval && daysRemoved > 0) {
            refundAmount = plugin.rentalManager.calculateProportionalRefund(rental, daysRemoved)

            if (refundAmount > 0) {
                val refundResult = plugin.rentalManager.issueRefund(rental, refundAmount, "time_removal", sender.name)

                if (refundResult != null && refundResult["success"] as Boolean) {
                    val actualRefund = refundResult["actualAmount"] as Double
                    val formattedAmount = String.format(plugin.configManager.currencyFormat, actualRefund)

                    sender.sendMessage(plugin.configManager.getMessage("duration-remove-refunded",
                        "{days}", daysRemoved.toString(),
                        "{region}", rental.regionName,
                        "{player}", rental.playerName,
                        "{amount}", formattedAmount))
                }
            }
        }

        // Remove the time
        rental.endDate = newEndDate

        plugin.signManager.updateSign(rental.regionName, world)
        plugin.rentalManager.saveAllRentals()

        if (refundAmount <= 0) {
            sender.sendMessage(plugin.configManager.getMessage("duration-remove-no-refund",
                "{days}", daysRemoved.toString(),
                "{region}", rental.regionName))
        }

        sender.sendMiniMessage("<yellow>New expiration: ${rental.formattedEndDate}")

        val refundLog = if (refundAmount > 0) " (refunded: \$${String.format("%.2f", refundAmount)})" else " (no refund)"
        plugin.logger.info("${sender.name} removed $timeRemoved from rental ${rental.regionName}$refundLog")
    }

    private fun handleSet(sender: CommandSender, rental: Rental, world: World, millis: Long) {
        rental.endDate = System.currentTimeMillis() + millis

        plugin.signManager.updateSign(rental.regionName, world)
        plugin.rentalManager.saveAllRentals()

        val newDuration = TimeUtils.formatDuration(millis)
        sender.sendMiniMessage("<green>Set rental duration for ${rental.regionName} to $newDuration")
        sender.sendMiniMessage("<yellow>New expiration: ${rental.formattedEndDate}")

        if (plugin.configManager.isDebug) {
            plugin.logger.info("${sender.name} set duration of ${rental.regionName} to $newDuration")
        }
    }

    private fun handleReset(sender: CommandSender, rental: Rental, world: World) {
        val defaultDays = plugin.configManager.defaultDuration

        // Check if extension refund is enabled
        var refundAmount = 0.0
        if (plugin.configManager.isRefundOnDurationReset) {
            refundAmount = rental.extensionCost

            if (refundAmount > 0) {
                val refundResult = plugin.rentalManager.issueRefund(rental, refundAmount, "duration_reset", sender.name)

                if (refundResult != null && refundResult["success"] as Boolean) {
                    val actualRefund = refundResult["actualAmount"] as Double
                    val formattedAmount = String.format(plugin.configManager.currencyFormat, actualRefund)

                    sender.sendMiniMessage("<green>Refunded extension costs: <gold>$formattedAmount")

                    // Notify player if online
                    plugin.server.getPlayer(rental.playerUUID)?.let { player ->
                        player.sendMiniMessage("<green>Your extension costs for <yellow>${rental.regionName}<green> have been refunded: <gold>$formattedAmount")
                    }
                }
            }
        }

        // Reset time to default duration
        rental.resetTime(defaultDays)

        plugin.signManager.updateSign(rental.regionName, world)
        plugin.rentalManager.saveAllRentals()

        sender.sendMiniMessage("<green>Reset duration for <yellow>${rental.regionName}<green> to default: <gold>$defaultDays days")
        sender.sendMiniMessage("<yellow>New expiration: ${rental.formattedEndDate}")

        val refundLog = if (refundAmount > 0) " with refund: \$${String.format("%.2f", refundAmount)}" else ""
        plugin.logger.info("${sender.name} reset duration of ${rental.regionName} to default ($defaultDays days)$refundLog")
    }

    /**
     * Parses compound time strings like "2 days 3 hours 30 minutes" or "2d 3h 30m".
     */
    private fun parseTimeString(timeStr: String): Long {
        var totalMillis = 0L

        // Convert shorthand to full format
        val normalized = timeStr.lowercase()
            .replace(Regex("(\\d+)d\\b"), "$1 days")
            .replace(Regex("(\\d+)h\\b"), "$1 hours")
            .replace(Regex("(\\d+)m\\b"), "$1 minutes")

        val matcher = TIME_PATTERN.matcher(normalized)

        while (matcher.find()) {
            val amount = matcher.group(1).toIntOrNull() ?: continue
            val unit = matcher.group(2).lowercase()

            totalMillis += when {
                unit.startsWith("day") -> amount * TimeUtils.DAY_MS
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * TimeUtils.HOUR_MS
                unit.startsWith("minute") || unit.startsWith("min") -> amount * TimeUtils.MINUTE_MS
                else -> 0L
            }
        }

        return totalMillis
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMiniMessage("<gold>=== Duration Command Usage ===")
        sender.sendMiniMessage("<yellow>/zrduration add <region> <time> [--charge]")
        sender.sendMiniMessage("<gray>  Example: /zrduration add shop1 2 days 3 hours 30 minutes")
        sender.sendMiniMessage("<gray>  Add --charge to charge the player for the time added")
        sender.sendMiniMessage("<yellow>/zrduration remove <region> <time>")
        sender.sendMiniMessage("<gray>  Example: /zrduration remove shop1 1 hour 30 mins")
        sender.sendMiniMessage("<gray>  Automatically refunds proportionally if configured")
        sender.sendMiniMessage("<yellow>/zrduration set <region> <time>")
        sender.sendMiniMessage("<gray>  Example: /zrduration set shop1 7 days")
        sender.sendMiniMessage("<yellow>/zrduration reset <region>")
        sender.sendMiniMessage("<gray>  Example: /zrduration reset shop1")
        sender.sendMiniMessage("<gray>  Resets to default duration, refunds extensions if configured")
        sender.sendMiniMessage("")
        sender.sendMiniMessage("<aqua>Time formats: days, hours, minutes")
        sender.sendMiniMessage("<aqua>Short forms: d, h, m (e.g., 2d 3h 30m)")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("zonerental.admin.duration")) return emptyList()

        return when (args.size) {
            1 -> ACTIONS.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> plugin.rentalManager.allRentals
                .map { it.regionName }
                .filter { it.startsWith(args[1], ignoreCase = true) }
            else -> {
                val lastArg = args.lastOrNull() ?: ""
                if (lastArg.matches(Regex("\\d+"))) {
                    listOf("days", "hours", "minutes")
                } else {
                    listOf("1", "2", "3", "5", "7", "10", "30")
                }
            }
        }
    }
}
