package com.regionrental.config

import com.regionrental.extensions.color
import com.regionrental.extensions.withPlaceholders
import org.bukkit.command.CommandSender

/**
 * DSL for formatting and sending plugin messages.
 * Provides a cleaner API for working with ConfigManager messages.
 */
class MessageFormatter(private val messages: Map<String, String>, private val prefix: String = "") {

    /**
     * Gets a message by key and returns a builder for fluent API.
     */
    operator fun get(key: String): MessageBuilder =
        MessageBuilder(messages[key] ?: "&cMissing message: $key", prefix)

    /**
     * Gets a raw message without the prefix.
     */
    fun getRaw(key: String): String =
        (messages[key] ?: "&cMissing message: $key").color()

    /**
     * Checks if a message key exists.
     */
    fun hasMessage(key: String): Boolean = key in messages

    /**
     * Builder for message formatting with fluent API.
     */
    class MessageBuilder(private var template: String, private val prefix: String) {

        private val replacements = mutableMapOf<String, String>()

        /**
         * Adds a placeholder replacement.
         */
        infix fun with(pair: Pair<String, Any>): MessageBuilder {
            replacements["{${pair.first}}"] = pair.second.toString()
            return this
        }

        /**
         * Adds multiple placeholder replacements.
         */
        fun with(vararg pairs: Pair<String, Any>): MessageBuilder {
            pairs.forEach { (key, value) ->
                replacements["{$key}"] = value.toString()
            }
            return this
        }

        /**
         * Adds a placeholder replacement using map syntax.
         */
        fun withMap(map: Map<String, Any>): MessageBuilder {
            map.forEach { (key, value) ->
                replacements["{$key}"] = value.toString()
            }
            return this
        }

        /**
         * Builds the final message string with prefix and color codes.
         */
        fun build(): String {
            var result = prefix + template
            replacements.forEach { (key, value) ->
                result = result.replace(key, value)
            }
            return result.color()
        }

        /**
         * Builds without prefix.
         */
        fun buildRaw(): String {
            var result = template
            replacements.forEach { (key, value) ->
                result = result.replace(key, value)
            }
            return result.color()
        }

        /**
         * Sends the formatted message to a CommandSender.
         */
        infix fun sendTo(sender: CommandSender) {
            sender.sendMessage(build())
        }

        /**
         * Sends the formatted message without prefix.
         */
        fun sendRawTo(sender: CommandSender) {
            sender.sendMessage(buildRaw())
        }

        /**
         * Converts to string (builds the message).
         */
        override fun toString(): String = build()
    }

    companion object {
        /**
         * Creates a MessageFormatter from a ConfigManager.
         * Note: Requires access to ConfigManager's internal messages map.
         */
        @JvmStatic
        fun create(messages: Map<String, String>, prefix: String): MessageFormatter =
            MessageFormatter(messages, prefix)
    }
}

/**
 * Extension function for quick message formatting.
 * Usage: "Hello {name}!".format("name" to "Steve")
 */
fun String.formatWith(vararg pairs: Pair<String, Any>): String {
    var result = this
    pairs.forEach { (key, value) ->
        result = result.replace("{$key}", value.toString())
    }
    return result.color()
}

/**
 * Inline class for type-safe placeholder keys.
 */
@JvmInline
value class PlaceholderKey(val key: String) {
    infix fun to(value: Any): Pair<String, String> = key to value.toString()
}

/**
 * Common placeholder keys used throughout the plugin.
 */
object Placeholders {
    val REGION = PlaceholderKey("region")
    val PLAYER = PlaceholderKey("player")
    val PRICE = PlaceholderKey("price")
    val DAYS = PlaceholderKey("days")
    val TIME = PlaceholderKey("time")
    val AMOUNT = PlaceholderKey("amount")
    val PREFIX = PlaceholderKey("prefix")
    val WORLD = PlaceholderKey("world")
    val GROUP = PlaceholderKey("group")
    val COUNT = PlaceholderKey("count")
}
