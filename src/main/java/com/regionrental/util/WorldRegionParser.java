package com.regionrental.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility class for parsing region arguments with world awareness.
 * Supports both explicit "world:region" format and implicit region names (infers from player's world).
 */
public class WorldRegionParser {

    /**
     * Parsed region with world context
     */
    public static class ParsedRegion {
        public final String worldName;
        public final String regionName;

        public ParsedRegion(String worldName, String regionName) {
            this.worldName = worldName;
            this.regionName = regionName;
        }

        /**
         * Get composite key in format "world:region"
         */
        public String getCompositeKey() {
            return worldName + ":" + regionName;
        }

        /**
         * Get the World object
         */
        public World getWorld() {
            return Bukkit.getWorld(worldName);
        }
    }

    /**
     * Parse region argument with world inference
     *
     * @param arg The argument (e.g., "shop1" or "world:shop1")
     * @param sender Command sender (for world inference)
     * @return ParsedRegion or null if invalid
     */
    public static ParsedRegion parse(String arg, CommandSender sender) {
        // Check if explicit world:region format
        if (arg.contains(":")) {
            String[] parts = arg.split(":", 2);
            String worldName = parts[0];
            String regionName = parts[1];

            // Validate world exists
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null; // Invalid world
            }

            return new ParsedRegion(worldName, regionName);
        }

        // No explicit world - infer from sender
        if (sender instanceof Player) {
            Player player = (Player) sender;
            return new ParsedRegion(player.getWorld().getName(), arg);
        }

        // Console without explicit world - invalid
        return null;
    }

    /**
     * Extract region name from composite key "world:region"
     *
     * @param compositeKey The composite key
     * @return Region name or the original key if no ":" found
     */
    public static String extractRegionName(String compositeKey) {
        if (compositeKey.contains(":")) {
            String[] parts = compositeKey.split(":", 2);
            if (parts.length == 2 && !parts[1].isEmpty()) {
                return parts[1];
            }
        }
        return compositeKey;
    }

    /**
     * Extract world name from composite key "world:region"
     *
     * @param compositeKey The composite key
     * @return World name or null if no ":" found
     */
    public static String extractWorldName(String compositeKey) {
        if (compositeKey.contains(":")) {
            return compositeKey.split(":", 2)[0];
        }
        return null;
    }
}
