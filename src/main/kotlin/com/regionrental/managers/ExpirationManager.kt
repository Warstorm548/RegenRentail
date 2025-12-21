package com.regionrental.managers

import com.regionrental.RegionRental
import com.regionrental.extensions.color
import org.bukkit.Bukkit
import org.bukkit.Sound

/**
 * Manages rental expiration checking and warning notifications.
 */
class ExpirationManager(private val plugin: RegionRental) {

    companion object {
        // Warning thresholds in hours
        private val WARNING_HOURS = intArrayOf(24, 12, 6, 1)
    }

    fun checkExpiredRentals() {
        val allRentals = plugin.rentalManager.allRentals

        for (rental in allRentals) {
            if (rental.isExpired) {
                handleExpiration(rental)
            } else {
                checkWarnings(rental)
            }
        }
    }

    private fun handleExpiration(rental: Rental) {
        // Notify player if online
        Bukkit.getPlayer(rental.playerUUID)?.let { player ->
            if (player.isOnline) {
                player.sendMessage(
                    plugin.configManager.getMessage("rental-expired", "{region}", rental.regionName)
                )
            }
        }

        // Expire the rental (CRITICAL FIX: use world-aware method)
        val world = plugin.server.getWorld(rental.worldName)
        if (world == null) {
            plugin.logger.warning(
                "Cannot expire rental for ${rental.regionName} - world '${rental.worldName}' not found!"
            )
            return
        }
        plugin.rentalManager.expireRental(rental.regionName, world)

        plugin.logger.info(
            "Expired rental for ${rental.worldName}:${rental.regionName} (Player: ${rental.playerName})"
        )
    }

    private fun checkWarnings(rental: Rental) {
        val hoursRemaining = rental.hoursRemaining

        for (warningHour in WARNING_HOURS) {
            val warningKey = "warning_${warningHour}h"

            if (hoursRemaining in 1..warningHour) {
                if (!rental.hasWarningBeenSent(warningKey)) {
                    sendWarning(rental, warningHour)
                    rental.markWarningSent(warningKey)
                    break // Only send one warning at a time
                }
            }
        }
    }

    private fun sendWarning(rental: Rental, hours: Int) {
        val player = Bukkit.getPlayer(rental.playerUUID) ?: return
        if (!player.isOnline) return

        val message = plugin.configManager.getMessage(
            "rental-expiring-soon",
            "{region}", rental.regionName,
            "{time}", formatTime(hours)
        )
        player.sendMessage(message)

        // Send title if configured
        if (plugin.config.getBoolean("notifications.title-enabled", true)) {
            val title = "&c&lRental Expiring!".color()
            val subtitle = "&e${rental.regionName} - ${formatTime(hours)}".color()

            player.sendTitle(title, subtitle, 10, 60, 20)
        }

        // Play sound if configured
        if (plugin.config.getBoolean("notifications.sound-enabled", true)) {
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        }
    }

    private fun formatTime(hours: Int): String = when {
        hours >= 24 -> {
            val days = hours / 24
            "$days day${if (days > 1) "s" else ""}"
        }
        else -> "$hours hour${if (hours > 1) "s" else ""}"
    }
}
