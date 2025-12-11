package com.regionrental.managers;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages teleport cooldowns for players to prevent spam teleporting
 */
public class TeleportCooldownManager {

    private final Map<UUID, Long> cooldowns;
    private final int cooldownSeconds;

    /**
     * Create a new cooldown manager
     * @param cooldownSeconds Cooldown duration in seconds (0 or negative to disable)
     */
    public TeleportCooldownManager(int cooldownSeconds) {
        this.cooldowns = new HashMap<>();
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Check if a player is currently on cooldown
     * @param player The player to check
     * @return true if player is on cooldown, false otherwise
     */
    public boolean isOnCooldown(Player player) {
        if (cooldownSeconds <= 0) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!cooldowns.containsKey(uuid)) {
            return false;
        }

        long lastTeleport = cooldowns.get(uuid);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;

        return (currentTime - lastTeleport) < cooldownMillis;
    }

    /**
     * Get the remaining cooldown time for a player in seconds
     * @param player The player to check
     * @return Remaining seconds, or 0 if not on cooldown
     */
    public int getRemainingCooldown(Player player) {
        if (!isOnCooldown(player)) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        long lastTeleport = cooldowns.get(uuid);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        long elapsed = currentTime - lastTeleport;
        long remaining = cooldownMillis - elapsed;

        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Start cooldown for a player
     * @param player The player to apply cooldown to
     */
    public void startCooldown(Player player) {
        if (cooldownSeconds <= 0) {
            return;
        }
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Clear cooldown for a specific player
     * @param player The player to clear cooldown for
     */
    public void clearCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /**
     * Clear all cooldowns (useful for plugin reload)
     */
    public void clearAll() {
        cooldowns.clear();
    }

    /**
     * Removes expired cooldowns from memory to prevent memory leaks.
     * Should be called periodically (e.g., every 10 minutes).
     * @return Number of expired entries removed
     */
    public int cleanupExpired() {
        if (cooldownSeconds <= 0 || cooldowns.isEmpty()) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        int removed = 0;

        // Use iterator to safely remove while iterating
        var iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long elapsed = currentTime - entry.getValue();
            // Remove if cooldown has expired (add 60 second buffer)
            if (elapsed > cooldownMillis + 60000L) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Get current number of tracked cooldowns (for debugging/monitoring)
     */
    public int size() {
        return cooldowns.size();
    }
}
