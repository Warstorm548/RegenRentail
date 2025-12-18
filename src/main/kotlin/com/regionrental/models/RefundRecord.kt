package com.regionrental.models

import com.regionrental.util.TimeUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents a single refund transaction for audit trail.
 * Replaces the inner class in Rental.java with a Kotlin data class.
 */
data class RefundRecord(
    val amount: Double,
    val timestamp: Long,
    val reason: String,
    val adminName: String
) {
    /**
     * Returns the timestamp formatted as "MM/dd HH:mm".
     */
    val formattedTimestamp: String
        get() = TimeUtils.formatTimestamp(timestamp)

    /**
     * Returns the timestamp as a LocalDateTime object.
     */
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )

    companion object {
        /**
         * Creates a RefundRecord with the current timestamp.
         */
        @JvmStatic
        fun now(amount: Double, reason: String, adminName: String): RefundRecord =
            RefundRecord(amount, System.currentTimeMillis(), reason, adminName)

        /**
         * Creates a RefundRecord from a map (for deserialization from YAML).
         */
        @JvmStatic
        fun fromMap(map: Map<String, Any>): RefundRecord? {
            val amount = (map["amount"] as? Number)?.toDouble() ?: return null
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return null
            val reason = map["reason"] as? String ?: return null
            val adminName = map["admin"] as? String ?: return null
            return RefundRecord(amount, timestamp, reason, adminName)
        }
    }

    /**
     * Converts this RefundRecord to a map (for serialization to YAML).
     */
    fun toMap(): Map<String, Any> = mapOf(
        "amount" to amount,
        "timestamp" to timestamp,
        "reason" to reason,
        "admin" to adminName
    )
}
