package com.regionrental.managers

import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.ceil

/**
 * Manages teleport cooldowns for players to prevent spam teleporting
 */
class TeleportCooldownManager(private val cooldownSeconds: Int) {

    private val cooldowns = mutableMapOf<UUID, Long>()

    /**
     * Check if a player is currently on cooldown
     */
    fun isOnCooldown(player: Player): Boolean {
        if (cooldownSeconds <= 0) return false

        val lastTeleport = cooldowns[player.uniqueId] ?: return false
        val elapsed = System.currentTimeMillis() - lastTeleport

        return elapsed < cooldownSeconds * 1000L
    }

    /**
     * Get the remaining cooldown time for a player in seconds
     */
    fun getRemainingCooldown(player: Player): Int {
        if (!isOnCooldown(player)) return 0

        val lastTeleport = cooldowns[player.uniqueId] ?: return 0
        val elapsed = System.currentTimeMillis() - lastTeleport
        val remaining = (cooldownSeconds * 1000L) - elapsed

        return ceil(remaining / 1000.0).toInt()
    }

    /**
     * Start cooldown for a player
     */
    fun startCooldown(player: Player) {
        if (cooldownSeconds <= 0) return
        cooldowns[player.uniqueId] = System.currentTimeMillis()
    }

    /**
     * Clear cooldown for a specific player
     */
    fun clearCooldown(player: Player) {
        cooldowns.remove(player.uniqueId)
    }

    /**
     * Clear all cooldowns (useful for plugin reload)
     */
    fun clearAll() {
        cooldowns.clear()
    }

    /**
     * Removes expired cooldowns from memory to prevent memory leaks.
     * Should be called periodically (e.g., every 10 minutes).
     * @return Number of expired entries removed
     */
    fun cleanupExpired(): Int {
        if (cooldownSeconds <= 0 || cooldowns.isEmpty()) return 0

        val currentTime = System.currentTimeMillis()
        val cooldownMillis = cooldownSeconds * 1000L
        var removed = 0

        val iterator = cooldowns.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val elapsed = currentTime - entry.value
            // Remove if cooldown has expired (add 60 second buffer)
            if (elapsed > cooldownMillis + 60_000L) {
                iterator.remove()
                removed++
            }
        }

        return removed
    }

    /**
     * Get current number of tracked cooldowns (for debugging/monitoring)
     */
    fun size(): Int = cooldowns.size
}
