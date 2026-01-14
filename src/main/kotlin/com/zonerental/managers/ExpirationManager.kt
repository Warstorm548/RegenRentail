package com.zonerental.managers

import com.github.shynixn.mccoroutine.bukkit.launch
import com.zonerental.ZoneRental
import com.zonerental.extensions.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import java.time.Duration

/**
 * Manages rental expiration checking and warning notifications.
 */
class ExpirationManager(private val plugin: ZoneRental) {

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

        // Launch async expiration using MCCoroutine
        plugin.launch {
            plugin.rentalManager.expireRentalAsync(rental.regionName, world)
        }

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
            val titleComponent = "<red><bold>Rental Expiring!".toComponent()
            val subtitleComponent = "<yellow>${rental.regionName} - ${formatTime(hours)}".toComponent()

            player.showTitle(Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.times(
                    Duration.ofMillis(500),   // fadeIn (10 ticks = 500ms)
                    Duration.ofMillis(3000),  // stay (60 ticks = 3000ms)
                    Duration.ofMillis(1000)   // fadeOut (20 ticks = 1000ms)
                )
            ))
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
