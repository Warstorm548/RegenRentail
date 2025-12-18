package com.regionrental.extensions

import org.bukkit.ChatColor

/**
 * Extension functions for String operations commonly used throughout the plugin.
 */

/**
 * Translates color codes using '&' as the alternate color code character.
 * Example: "&cRed text".color() -> "Red text" (in red)
 */
fun String.color(): String = ChatColor.translateAlternateColorCodes('&', this)

/**
 * Returns this string if not null/blank, otherwise returns the default.
 */
fun String?.orDefault(default: String): String = if (this.isNullOrBlank()) default else this

/**
 * Replaces multiple placeholders in a string.
 * Example: "Hello {name}!".withPlaceholders("{name}" to "Steve") -> "Hello Steve!"
 */
fun String.withPlaceholders(vararg pairs: Pair<String, String>): String =
    pairs.fold(this) { acc, (key, value) -> acc.replace(key, value) }

/**
 * Replaces placeholders using a map.
 */
fun String.withPlaceholders(replacements: Map<String, String>): String =
    replacements.entries.fold(this) { acc, (key, value) -> acc.replace(key, value) }

/**
 * Combines color translation and placeholder replacement.
 */
fun String.formatMessage(vararg pairs: Pair<String, String>): String =
    withPlaceholders(*pairs).color()

/**
 * Parses a composite key in format "world:region" into a Pair.
 * Returns null if the format is invalid.
 */
fun String.parseCompositeKey(): Pair<String, String>? {
    val parts = split(":", limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[0] to parts[1]
    } else null
}

/**
 * Extracts the world name from a composite key "world:region".
 */
fun String.extractWorldName(): String? = parseCompositeKey()?.first

/**
 * Extracts the region name from a composite key "world:region".
 */
fun String.extractRegionName(): String? = parseCompositeKey()?.second

/**
 * Creates a composite key from world and region names.
 */
fun compositeKey(worldName: String, regionName: String): String = "$worldName:$regionName"
