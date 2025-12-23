package com.regionrental.commands

import com.regionrental.RegionRental
import com.regionrental.extensions.checkPermission
import com.regionrental.extensions.color
import com.regionrental.managers.Rental
import com.regionrental.util.TimeUtils
import com.regionrental.util.WorldRegionParser
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.regex.Pattern

/**
 * Command for modifying rental durations.
 * Usage: /rrduration <add|remove|set|reset> <region> [time] [--charge]
 */
class DurationCommand(private val plugin: RegionRental) : CommandExecutor, TabCompleter {

    companion object {
        private val ACTIONS = listOf("add", "remove", "set", "reset")
        private val TIME_PATTERN = Pattern.compile("(\\d+)\\s*(days?|hours?|hrs?|minutes?|mins?)")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        sender.checkPermission("regionrental.admin.duration", plugin.configManager.getMessage("no-permission"))
            ?: return true

        if (args.size < 2) {
            showUsage(sender)
            return true
        }

        val action = args[0].lowercase()

        // Parse region with world inference
        val parsed = WorldRegionParser.parse(args[1], sender) ?: run {
            sender.sendMessage("${ChatColor.RED}Invalid format! Console must use world:region format (e.g., world:shop1)")
            return true
        }

        val world = parsed.getWorld() ?: run {
            sender.sendMessage("${ChatColor.RED}World '${parsed.worldName}' is not loaded!")
            return true
        }
        val regionName = parsed.regionName

        // Check if region is rented
        val rental = plugin.rentalManager.getRental(regionName, world) ?: run {
            sender.sendMessage("${ChatColor.RED}Region ${parsed.getCompositeKey()} is not currently rented!")
            return true
        }

        // Handle reset action (no time parameter needed)
        if (action == "reset") {
            handleReset(sender, rental, world)
            return true
        }

        // Other actions require time parameter
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /rrduration $action <region> <time>")
            sender.sendMessage("${ChatColor.GRAY}Example: /rrduration $action shop1 7d")
            return true
        }

        // Parse --charge flag and time arguments
        val chargePlayer = args.any { it.equals("--charge", ignoreCase = true) }
        val timeArgs = args.drop(2).filterNot { it.equals("--charge", ignoreCase = true) }

        if (timeArgs.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}No time specified!")
            showUsage(sender)
            return true
        }

        val timeString = timeArgs.joinToString(" ")
        val millisToModify = parseTimeString(timeString)

        if (millisToModify <= 0) {
            sender.sendMessage("${ChatColor.RED}Invalid time format! Use: <number> <days|hours|minutes>")
            sender.sendMessage("${ChatColor.YELLOW}Example: 2 days 3 hours 30 minutes")
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
                sender.sendMessage("${ChatColor.RED}Player must be online to charge for time addition!")
                return
            }

            // Attempt to charge the player
            val charged = plugin.rentalManager.chargeDurationAdd(rental, days, player)

            if (!charged) {
                sender.sendMessage("${ChatColor.RED}Failed to charge player! They may not have enough money.")
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

            sender.sendMessage("${ChatColor.YELLOW}New expiration: ${rental.formattedEndDate}")

            // Notify player
            player.sendMessage("${ChatColor.GREEN}Admin ${sender.name} added $timeAdded to your rental of ${ChatColor.YELLOW}${rental.regionName}")
            player.sendMessage("${ChatColor.GREEN}You were charged: ${ChatColor.GOLD}$formattedAmount")

            plugin.logger.info("${sender.name} added $timeAdded to rental ${rental.regionName} (charged $formattedAmount)")
        } else {
            // Free addition
            rental.endDate = rental.endDate + millis

            plugin.signManager.updateSign(rental.regionName, world)
            plugin.rentalManager.saveAllRentals()

            sender.sendMessage(plugin.configManager.getMessage("duration-add-free",
                "{days}", timeAdded,
                "{region}", rental.regionName))

            sender.sendMessage("${ChatColor.YELLOW}New expiration: ${rental.formattedEndDate}")

            plugin.logger.info("${sender.name} added $timeAdded to rental ${rental.regionName} (free)")
        }
    }

    private fun handleRemove(sender: CommandSender, rental: Rental, world: World, millis: Long) {
        val currentTime = System.currentTimeMillis()
        val newEndDate = rental.endDate - millis

        // Don't allow setting time in the past
        if (newEndDate <= currentTime) {
            sender.sendMessage("${ChatColor.RED}Cannot remove that much time - would expire the rental!")
            sender.sendMessage("${ChatColor.YELLOW}Time remaining: ${TimeUtils.formatDuration(rental.timeRemaining)}")
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

        sender.sendMessage("${ChatColor.YELLOW}New expiration: ${rental.formattedEndDate}")

        val refundLog = if (refundAmount > 0) " (refunded: \$${String.format("%.2f", refundAmount)})" else " (no refund)"
        plugin.logger.info("${sender.name} removed $timeRemoved from rental ${rental.regionName}$refundLog")
    }

    private fun handleSet(sender: CommandSender, rental: Rental, world: World, millis: Long) {
        rental.endDate = System.currentTimeMillis() + millis

        plugin.signManager.updateSign(rental.regionName, world)
        plugin.rentalManager.saveAllRentals()

        val newDuration = TimeUtils.formatDuration(millis)
        sender.sendMessage("${ChatColor.GREEN}Set rental duration for ${rental.regionName} to $newDuration")
        sender.sendMessage("${ChatColor.YELLOW}New expiration: ${rental.formattedEndDate}")

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

                    sender.sendMessage("${ChatColor.GREEN}Refunded extension costs: ${ChatColor.GOLD}$formattedAmount")

                    // Notify player if online
                    plugin.server.getPlayer(rental.playerUUID)?.let { player ->
                        player.sendMessage("${ChatColor.GREEN}Your extension costs for ${ChatColor.YELLOW}${rental.regionName}${ChatColor.GREEN} have been refunded: ${ChatColor.GOLD}$formattedAmount")
                    }
                }
            }
        }

        // Reset time to default duration
        rental.resetTime(defaultDays)

        plugin.signManager.updateSign(rental.regionName, world)
        plugin.rentalManager.saveAllRentals()

        sender.sendMessage("${ChatColor.GREEN}Reset duration for ${ChatColor.YELLOW}${rental.regionName}${ChatColor.GREEN} to default: ${ChatColor.GOLD}$defaultDays days")
        sender.sendMessage("${ChatColor.YELLOW}New expiration: ${rental.formattedEndDate}")

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
            .replace(Regex("(\\d+)s\\b"), "$1 seconds")

        val matcher = TIME_PATTERN.matcher(normalized)

        while (matcher.find()) {
            val amount = matcher.group(1).toIntOrNull() ?: continue
            val unit = matcher.group(2).lowercase()

            totalMillis += when {
                unit.startsWith("day") -> amount * TimeUtils.DAY_MS
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * TimeUtils.HOUR_MS
                unit.startsWith("minute") || unit.startsWith("min") -> amount * TimeUtils.MINUTE_MS
                unit.startsWith("second") || unit.startsWith("sec") -> amount * TimeUtils.SECOND_MS
                else -> 0L
            }
        }

        return totalMillis
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}=== Duration Command Usage ===")
        sender.sendMessage("${ChatColor.YELLOW}/rrduration add <region> <time> [--charge]")
        sender.sendMessage("${ChatColor.GRAY}  Example: /rrduration add shop1 2 days 3 hours 30 minutes")
        sender.sendMessage("${ChatColor.GRAY}  Add --charge to charge the player for the time added")
        sender.sendMessage("${ChatColor.YELLOW}/rrduration remove <region> <time>")
        sender.sendMessage("${ChatColor.GRAY}  Example: /rrduration remove shop1 1 hour 30 mins")
        sender.sendMessage("${ChatColor.GRAY}  Automatically refunds proportionally if configured")
        sender.sendMessage("${ChatColor.YELLOW}/rrduration set <region> <time>")
        sender.sendMessage("${ChatColor.GRAY}  Example: /rrduration set shop1 7 days")
        sender.sendMessage("${ChatColor.YELLOW}/rrduration reset <region>")
        sender.sendMessage("${ChatColor.GRAY}  Example: /rrduration reset shop1")
        sender.sendMessage("${ChatColor.GRAY}  Resets to default duration, refunds extensions if configured")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.AQUA}Time formats: days, hours, minutes")
        sender.sendMessage("${ChatColor.AQUA}Short forms: d, h, m (e.g., 2d 3h 30m)")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (!sender.hasPermission("regionrental.admin.duration")) return emptyList()

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
