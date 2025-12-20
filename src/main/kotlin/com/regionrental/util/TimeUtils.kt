package com.regionrental.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utility functions for time and duration operations.
 */
object TimeUtils {

    /** Milliseconds in one second */
    const val SECOND_MS: Long = 1000L

    /** Milliseconds in one minute */
    const val MINUTE_MS: Long = 60 * SECOND_MS

    /** Milliseconds in one hour */
    const val HOUR_MS: Long = 60 * MINUTE_MS

    /** Milliseconds in one day */
    const val DAY_MS: Long = 24 * HOUR_MS

    /** Default date-time format pattern */
    const val DEFAULT_FORMAT: String = "MM/dd HH:mm"

    private val defaultFormatter = DateTimeFormatter.ofPattern(DEFAULT_FORMAT)

    /**
     * Converts days to milliseconds.
     */
    @JvmStatic
    fun daysToMillis(days: Int): Long = days * DAY_MS

    /**
     * Converts days to milliseconds (Long version).
     */
    @JvmStatic
    fun daysToMillis(days: Long): Long = days * DAY_MS

    /**
     * Converts milliseconds to days (rounds down).
     */
    @JvmStatic
    fun millisToDays(millis: Long): Int = (millis / DAY_MS).toInt()

    /**
     * Converts milliseconds to hours (rounds down).
     */
    @JvmStatic
    fun millisToHours(millis: Long): Int = (millis / HOUR_MS).toInt()

    /**
     * Converts milliseconds to minutes (rounds down).
     */
    @JvmStatic
    fun millisToMinutes(millis: Long): Int = (millis / MINUTE_MS).toInt()

    /**
     * Formats a timestamp (epoch millis) to a human-readable string.
     */
    @JvmStatic
    fun formatTimestamp(timestamp: Long, pattern: String = DEFAULT_FORMAT): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return if (pattern == DEFAULT_FORMAT) {
            defaultFormatter.format(dateTime)
        } else {
            DateTimeFormatter.ofPattern(pattern).format(dateTime)
        }
    }

    /**
     * Formats a duration in milliseconds to a human-readable string.
     * Example: "2 days 5 hours" or "3 hours 30 minutes"
     */
    @JvmStatic
    fun formatDuration(millis: Long): String {
        if (millis <= 0) return "0 minutes"

        val days = millis / DAY_MS
        val hours = (millis % DAY_MS) / HOUR_MS
        val minutes = (millis % HOUR_MS) / MINUTE_MS

        val parts = mutableListOf<String>()

        if (days > 0) {
            parts.add("$days day${if (days > 1) "s" else ""}")
        }
        if (hours > 0) {
            parts.add("$hours hour${if (hours > 1) "s" else ""}")
        }
        if (minutes > 0 && days == 0L) { // Only show minutes if less than a day
            parts.add("$minutes minute${if (minutes > 1) "s" else ""}")
        }

        return parts.ifEmpty { listOf("0 minutes") }.joinToString(" ")
    }

    /**
     * Formats a duration showing only the most significant unit.
     * Example: "2 days" or "5 hours"
     */
    @JvmStatic
    fun formatDurationShort(millis: Long): String {
        if (millis <= 0) return "0m"

        val days = millis / DAY_MS
        val hours = (millis % DAY_MS) / HOUR_MS
        val minutes = (millis % HOUR_MS) / MINUTE_MS

        return when {
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /**
     * Parses a time string like "7d", "24h", "30m" to milliseconds.
     * Returns null if parsing fails.
     */
    @JvmStatic
    fun parseTimeString(input: String): Long? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) return null

        val unit = trimmed.last()
        val value = trimmed.dropLast(1).toLongOrNull() ?: return null

        return when (unit) {
            'd' -> value * DAY_MS
            'h' -> value * HOUR_MS
            'm' -> value * MINUTE_MS
            's' -> value * SECOND_MS
            else -> null
        }
    }

    /**
     * Calculates end time from now plus the given days.
     */
    @JvmStatic
    fun endTimeFromNow(days: Int): Long = System.currentTimeMillis() + daysToMillis(days)

    /**
     * Checks if a timestamp has passed (is expired).
     */
    @JvmStatic
    fun isExpired(endTime: Long): Boolean = System.currentTimeMillis() > endTime

    /**
     * Gets the remaining time until expiration.
     * Returns 0 if already expired.
     */
    @JvmStatic
    fun timeRemaining(endTime: Long): Long = maxOf(0, endTime - System.currentTimeMillis())
}

/**
 * Extension function to convert Int days to milliseconds.
 */
val Int.days: Long get() = this * TimeUtils.DAY_MS

/**
 * Extension function to convert Int hours to milliseconds.
 */
val Int.hours: Long get() = this * TimeUtils.HOUR_MS

/**
 * Extension function to convert Int minutes to milliseconds.
 */
val Int.minutes: Long get() = this * TimeUtils.MINUTE_MS

/**
 * Extension function to convert Long to formatted duration string.
 */
fun Long.toFormattedDuration(): String = TimeUtils.formatDuration(this)

/**
 * Extension function to convert Long timestamp to formatted date string.
 */
fun Long.toFormattedDate(pattern: String = TimeUtils.DEFAULT_FORMAT): String =
    TimeUtils.formatTimestamp(this, pattern)
