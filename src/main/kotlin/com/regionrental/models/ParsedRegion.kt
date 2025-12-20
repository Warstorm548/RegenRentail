package com.regionrental.models

import com.regionrental.extensions.toWorld
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Represents a parsed region with world and region name.
 * Replaces WorldRegionParser.ParsedRegion inner class.
 */
data class ParsedRegion(
    val worldName: String,
    val regionName: String
) {
    /**
     * Gets the composite key in format "world:region".
     */
    val compositeKey: String
        get() = "$worldName:$regionName"

    /**
     * Gets the World object, or null if the world doesn't exist.
     */
    val world: World?
        get() = Bukkit.getWorld(worldName)

    /**
     * Gets the World object, throwing if the world doesn't exist.
     */
    fun getWorldOrThrow(): World = world
        ?: throw IllegalStateException("World '$worldName' does not exist")

    companion object {
        /**
         * Parses a region string that may be in format "world:region" or just "region".
         * If no world is specified, uses the sender's world (if player) or the default world.
         *
         * @param input The input string to parse
         * @param sender The command sender for context
         * @return ParsedRegion or null if parsing fails
         */
        @JvmStatic
        fun parse(input: String, sender: CommandSender?): ParsedRegion? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            return if (trimmed.contains(":")) {
                // Format: world:region
                val parts = trimmed.split(":", limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    null
                } else {
                    ParsedRegion(parts[0], parts[1])
                }
            } else {
                // Just region name - determine world from context
                val worldName = when (sender) {
                    is Player -> sender.world.name
                    else -> Bukkit.getWorlds().firstOrNull()?.name ?: "world"
                }
                ParsedRegion(worldName, trimmed)
            }
        }

        /**
         * Parses a region string with explicit world fallback.
         *
         * @param input The input string to parse
         * @param defaultWorld The world to use if none specified
         * @return ParsedRegion or null if parsing fails
         */
        @JvmStatic
        fun parse(input: String, defaultWorld: World): ParsedRegion? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            return if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    null
                } else {
                    ParsedRegion(parts[0], parts[1])
                }
            } else {
                ParsedRegion(defaultWorld.name, trimmed)
            }
        }

        /**
         * Creates a ParsedRegion from a composite key string "world:region".
         * Returns null if the format is invalid.
         */
        @JvmStatic
        fun fromCompositeKey(compositeKey: String): ParsedRegion? {
            val parts = compositeKey.split(":", limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                ParsedRegion(parts[0], parts[1])
            } else null
        }

        /**
         * Extracts just the world name from a composite key.
         */
        @JvmStatic
        fun extractWorldName(compositeKey: String): String? =
            fromCompositeKey(compositeKey)?.worldName

        /**
         * Extracts just the region name from a composite key.
         */
        @JvmStatic
        fun extractRegionName(compositeKey: String): String? =
            fromCompositeKey(compositeKey)?.regionName
    }
}
