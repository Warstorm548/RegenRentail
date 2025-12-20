package com.regionrental.extensions

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * Extension functions for Location operations.
 */

/**
 * Converts a Location to a string key in format "world:x:y:z".
 * Returns null if the world is null.
 */
fun Location.toKey(): String? {
    val worldName = world?.name ?: return null
    return "$worldName:$blockX:$blockY:$blockZ"
}

/**
 * Converts a Location to a string key, throwing if world is null.
 */
fun Location.toKeyOrThrow(): String {
    val worldName = world?.name ?: throw IllegalStateException("Location has no world")
    return "$worldName:$blockX:$blockY:$blockZ"
}

/**
 * Parses a location key string "world:x:y:z" back to a Location.
 * Returns null if parsing fails or world doesn't exist.
 */
fun String.toLocation(): Location? {
    val parts = split(":")
    if (parts.size != 4) return null

    val world = Bukkit.getWorld(parts[0]) ?: return null
    val x = parts[1].toIntOrNull() ?: return null
    val y = parts[2].toIntOrNull() ?: return null
    val z = parts[3].toIntOrNull() ?: return null

    return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
}

/**
 * Gets a World by name, returning null if not found.
 */
fun String.toWorld(): World? = Bukkit.getWorld(this)

/**
 * Gets a World by name, throwing if not found.
 */
fun String.toWorldOrThrow(): World = Bukkit.getWorld(this)
    ?: throw IllegalArgumentException("World '$this' does not exist")

/**
 * Creates a location key without the world component (just "x:y:z").
 */
fun Location.toLocalKey(): String = "$blockX:$blockY:$blockZ"

/**
 * Checks if this location is within the same block as another location.
 */
fun Location.isSameBlock(other: Location): Boolean =
    world == other.world && blockX == other.blockX && blockY == other.blockY && blockZ == other.blockZ
