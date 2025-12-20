package com.regionrental.managers

import org.bukkit.World
import java.util.UUID

/**
 * Kotlin extension functions for manager classes.
 * Provides more idiomatic Kotlin APIs while maintaining Java compatibility.
 */

// ============ RentalManager Extensions ============

/**
 * Gets all rentals for a player using Kotlin collection operations.
 * More concise alternative to the Java getPlayerRentals method.
 */
fun Map<String, Rental>.getPlayerRentals(
    playerIndex: Map<UUID, Set<String>>,
    playerUUID: UUID
): List<Rental> = playerIndex[playerUUID]?.mapNotNull { this[it] } ?: emptyList()

/**
 * Gets all rentals in a specific world.
 */
fun Map<String, Rental>.getRentalsInWorld(worldName: String): List<Rental> =
    values.filter { it.worldName == worldName }

/**
 * Gets all expired rentals.
 */
fun Map<String, Rental>.getExpiredRentals(): List<Rental> =
    values.filter { it.isExpired }

/**
 * Gets rentals expiring within the specified hours.
 */
fun Map<String, Rental>.getExpiringWithin(hours: Int): List<Rental> {
    val threshold = hours * 60L * 60L * 1000L
    return values.filter { rental ->
        val remaining = rental.timeRemaining
        remaining > 0 && remaining <= threshold
    }
}

/**
 * Finds a rental by player UUID (first match).
 */
fun Map<String, Rental>.findByPlayer(playerUUID: UUID): Rental? =
    values.find { it.playerUUID == playerUUID }

/**
 * Finds all rentals where the player is a member (not owner).
 */
fun Map<String, Rental>.findByMember(memberUUID: UUID): List<Rental> =
    values.filter { it.hasMember(memberUUID) }

/**
 * Gets rentals sorted by end date (soonest first).
 */
fun Map<String, Rental>.sortedByExpiration(): List<Rental> =
    values.sortedBy { it.endDate }

/**
 * Gets total revenue from all active rentals.
 */
fun Map<String, Rental>.totalRevenue(): Double =
    values.sumOf { it.totalPaid }

/**
 * Groups rentals by world.
 */
fun Map<String, Rental>.groupByWorld(): Map<String, List<Rental>> =
    values.groupBy { it.worldName }

/**
 * Groups rentals by player.
 */
fun Map<String, Rental>.groupByPlayer(): Map<UUID, List<Rental>> =
    values.groupBy { it.playerUUID }

// ============ Rental Collection Utilities ============

/**
 * Extension to check if any rental is expiring soon.
 */
fun Collection<Rental>.anyExpiringSoon(hoursThreshold: Int = 24): Boolean {
    val thresholdMillis = hoursThreshold * 60L * 60L * 1000L
    return any { rental ->
        val remaining = rental.timeRemaining
        remaining > 0 && remaining <= thresholdMillis
    }
}

/**
 * Calculates total value of a collection of rentals.
 */
fun Collection<Rental>.totalValue(): Double = sumOf { it.totalPaid }

/**
 * Gets composite keys for a collection of rentals.
 */
fun Collection<Rental>.compositeKeys(): List<String> = map { it.compositeKey }

/**
 * Filters to only active (non-expired) rentals.
 */
fun Collection<Rental>.active(): List<Rental> = filter { !it.isExpired }

/**
 * Filters to only expired rentals.
 */
fun Collection<Rental>.expired(): List<Rental> = filter { it.isExpired }

// ============ Sign/Region Utilities ============

/**
 * Creates a composite key from world and region name.
 */
fun compositeKey(worldName: String, regionName: String): String = "$worldName:$regionName"

/**
 * Creates a composite key from World and region name.
 */
fun compositeKey(world: World, regionName: String): String = "${world.name}:$regionName"

/**
 * Parses a composite key into world name and region name.
 */
fun parseCompositeKey(key: String): Pair<String, String>? {
    val parts = key.split(":", limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else null
}

// ============ UUID Utilities ============

/**
 * Safely parses a UUID string, returning null on failure.
 */
fun String.toUUIDOrNull(): UUID? = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    null
}

/**
 * Parses a list of UUID strings, filtering out invalid ones.
 */
fun List<String>.parseUUIDs(): List<UUID> = mapNotNull { it.toUUIDOrNull() }

/**
 * Converts a collection of UUIDs to a set of strings.
 */
fun Collection<UUID>.toStringSet(): Set<String> = map { it.toString() }.toSet()
