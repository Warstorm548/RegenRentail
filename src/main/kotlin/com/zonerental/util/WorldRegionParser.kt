package com.zonerental.util

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Utility class for parsing region arguments with world awareness.
 * Supports both explicit "world:region" format and implicit region names (infers from player's world).
 */
object WorldRegionParser {

    /**
     * Parsed region with world context.
     * Uses @JvmField for Java compatibility (direct field access).
     */
    class ParsedRegion(
        @JvmField val worldName: String,
        @JvmField val regionName: String
    ) {
        /**
         * Get composite key in format "world:region".
         */
        fun getCompositeKey(): String = "$worldName:$regionName"

        /**
         * Get the World object (may be null if world is unloaded).
         */
        fun getWorld(): World? = Bukkit.getWorld(worldName)
    }

    /**
     * Parse region argument with world inference.
     *
     * @param arg The argument (e.g., "shop1" or "world:shop1")
     * @param sender Command sender (for world inference)
     * @return ParsedRegion or null if invalid
     */
    @JvmStatic
    fun parse(arg: String, sender: CommandSender): ParsedRegion? {
        // Check if explicit world:region format
        if (":" in arg) {
            val parts = arg.split(":", limit = 2)
            val worldName = parts[0]
            val regionName = parts[1]

            // Validate world exists
            Bukkit.getWorld(worldName) ?: return null

            return ParsedRegion(worldName, regionName)
        }

        // No explicit world - infer from sender
        return (sender as? Player)?.let { player ->
            ParsedRegion(player.world.name, arg)
        }
    }

    /**
     * Extract region name from composite key "world:region".
     *
     * @param compositeKey The composite key
     * @return Region name or the original key if no ":" found
     */
    @JvmStatic
    fun extractRegionName(compositeKey: String?): String? {
        if (compositeKey == null) return null
        return if (":" in compositeKey) {
            compositeKey.split(":", limit = 2).getOrNull(1) ?: compositeKey
        } else {
            compositeKey
        }
    }

    /**
     * Extract world name from composite key "world:region".
     *
     * @param compositeKey The composite key
     * @return World name or null if no ":" found
     */
    @JvmStatic
    fun extractWorldName(compositeKey: String?): String? {
        if (compositeKey == null) return null
        return if (":" in compositeKey) {
            compositeKey.split(":", limit = 2).getOrNull(0)
        } else {
            null
        }
    }
}
