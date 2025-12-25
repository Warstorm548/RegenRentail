package com.zonerental.extensions

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Extension functions for Player and CommandSender operations.
 */

/**
 * Safely casts CommandSender to Player, returning null if not a player.
 */
fun CommandSender.asPlayerOrNull(): Player? = this as? Player

/**
 * Safely casts CommandSender to Player, throwing if not a player.
 */
fun CommandSender.asPlayer(): Player = this as? Player
    ?: throw IllegalStateException("Command sender is not a player")

/**
 * Checks if sender is a player and returns it, or sends a message and returns null.
 */
fun CommandSender.requirePlayer(message: String = "&cThis command can only be used by players."): Player? {
    return asPlayerOrNull() ?: run {
        sendMessage(message.color())
        null
    }
}

/**
 * Checks if sender has permission and returns true, or sends a message and returns false.
 */
fun CommandSender.requirePermission(
    permission: String,
    message: String = "&cYou don't have permission to use this command."
): Boolean {
    return if (hasPermission(permission)) {
        true
    } else {
        sendMessage(message.color())
        false
    }
}

/**
 * Checks if sender has permission, returning null if not (with message).
 * Useful for chaining: sender.checkPermission("perm")?.let { ... }
 */
fun CommandSender.checkPermission(
    permission: String,
    message: String = "&cYou don't have permission to use this command."
): CommandSender? {
    return if (hasPermission(permission)) this else {
        sendMessage(message.color())
        null
    }
}

/**
 * Sends a colored message to the sender.
 */
fun CommandSender.sendColoredMessage(message: String) {
    sendMessage(message.color())
}

/**
 * Sends a colored message with placeholders to the sender.
 */
fun CommandSender.sendColoredMessage(message: String, vararg placeholders: Pair<String, String>) {
    sendMessage(message.formatMessage(*placeholders))
}
