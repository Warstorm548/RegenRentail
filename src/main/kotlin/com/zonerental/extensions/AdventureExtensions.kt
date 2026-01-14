package com.zonerental.extensions

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Extension functions for Adventure Component API integration.
 * Provides MiniMessage parsing and legacy color code conversion.
 */

private val miniMessage = MiniMessage.miniMessage()
private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

/**
 * Parses a MiniMessage format string to Component.
 * Example: "<green>Hello <bold>World</bold>!".toComponent()
 */
fun String.toComponent(): Component = miniMessage.deserialize(this)

/**
 * Converts legacy '&' color codes to Component.
 * For backward compatibility during migration.
 * Example: "&aGreen &cRed".legacyToComponent()
 */
fun String.legacyToComponent(): Component = legacySerializer.deserialize(this)

/**
 * Converts legacy '&' codes to MiniMessage format.
 * Example: "&aGreen &cRed" -> "<green>Green <red>Red"
 */
fun String.toMiniMessage(): String {
    return this
        .replace("&0", "<black>")
        .replace("&1", "<dark_blue>")
        .replace("&2", "<dark_green>")
        .replace("&3", "<dark_aqua>")
        .replace("&4", "<dark_red>")
        .replace("&5", "<dark_purple>")
        .replace("&6", "<gold>")
        .replace("&7", "<gray>")
        .replace("&8", "<dark_gray>")
        .replace("&9", "<blue>")
        .replace("&a", "<green>")
        .replace("&b", "<aqua>")
        .replace("&c", "<red>")
        .replace("&d", "<light_purple>")
        .replace("&e", "<yellow>")
        .replace("&f", "<white>")
        .replace("&l", "<bold>")
        .replace("&o", "<italic>")
        .replace("&n", "<underlined>")
        .replace("&m", "<strikethrough>")
        .replace("&k", "<obfuscated>")
        .replace("&r", "<reset>")
        // Handle uppercase variants
        .replace("&A", "<green>")
        .replace("&B", "<aqua>")
        .replace("&C", "<red>")
        .replace("&D", "<light_purple>")
        .replace("&E", "<yellow>")
        .replace("&F", "<white>")
        .replace("&L", "<bold>")
        .replace("&O", "<italic>")
        .replace("&N", "<underlined>")
        .replace("&M", "<strikethrough>")
        .replace("&K", "<obfuscated>")
        .replace("&R", "<reset>")
}

/**
 * Parses a string that may contain either MiniMessage or legacy format.
 * Auto-detects format based on content.
 */
fun String.autoComponent(): Component {
    return if (contains('<') && contains('>')) {
        // Likely MiniMessage format
        miniMessage.deserialize(this)
    } else {
        // Legacy format
        legacySerializer.deserialize(this)
    }
}
