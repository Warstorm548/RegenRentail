package com.zonerental.commands

import com.zonerental.util.TimeUtils

/**
 * Sealed class representing duration modification actions.
 * Can be used to refactor DurationCommand for type-safe action handling.
 */
sealed class DurationAction {
    /**
     * Add time to a rental's duration.
     * @param millis The milliseconds to add
     * @param charge Whether to charge the player for the added time
     */
    data class Add(val millis: Long, val charge: Boolean = false) : DurationAction() {
        val days: Int get() = TimeUtils.millisToDays(millis)
        val formattedTime: String get() = TimeUtils.formatDuration(millis)
    }

    /**
     * Remove time from a rental's duration.
     * @param millis The milliseconds to remove
     */
    data class Remove(val millis: Long) : DurationAction() {
        val days: Int get() = TimeUtils.millisToDays(millis)
        val formattedTime: String get() = TimeUtils.formatDuration(millis)
    }

    /**
     * Set the rental duration to a specific time from now.
     * @param millis The duration in milliseconds from now
     */
    data class Set(val millis: Long) : DurationAction() {
        val days: Int get() = TimeUtils.millisToDays(millis)
        val formattedTime: String get() = TimeUtils.formatDuration(millis)
    }

    /**
     * Reset the rental duration to the default configured duration.
     */
    data object Reset : DurationAction()

    companion object {
        /**
         * Parses an action name to a DurationAction type.
         * Note: This returns the action type, not a fully constructed instance.
         */
        fun fromName(name: String): ActionType? = when (name.lowercase()) {
            "add" -> ActionType.ADD
            "remove" -> ActionType.REMOVE
            "set" -> ActionType.SET
            "reset" -> ActionType.RESET
            else -> null
        }

        /**
         * Creates an Add action from days.
         */
        @JvmStatic
        fun addDays(days: Int, charge: Boolean = false): Add =
            Add(TimeUtils.daysToMillis(days), charge)

        /**
         * Creates a Remove action from days.
         */
        @JvmStatic
        fun removeDays(days: Int): Remove =
            Remove(TimeUtils.daysToMillis(days))

        /**
         * Creates a Set action from days.
         */
        @JvmStatic
        fun setDays(days: Int): Set =
            Set(TimeUtils.daysToMillis(days))
    }

    /**
     * Enum for action type identification without full construction.
     */
    enum class ActionType {
        ADD, REMOVE, SET, RESET
    }
}

/**
 * Result of executing a duration action.
 */
sealed class DurationResult {
    /**
     * Duration was successfully modified.
     */
    data class Success(
        val newEndTime: Long,
        val oldEndTime: Long,
        val message: String
    ) : DurationResult() {
        val timeDifference: Long get() = newEndTime - oldEndTime
    }

    /**
     * Duration modification failed.
     */
    data class Failure(val reason: String) : DurationResult()

    /**
     * Duration modification requires player to be online (for charging).
     */
    data class PlayerOffline(val playerName: String) : DurationResult()

    /**
     * Player doesn't have enough funds for the charge.
     */
    data class InsufficientFunds(
        val required: Double,
        val available: Double
    ) : DurationResult()

    /**
     * A refund was issued as part of the duration change.
     */
    data class SuccessWithRefund(
        val newEndTime: Long,
        val oldEndTime: Long,
        val refundAmount: Double,
        val message: String
    ) : DurationResult()
}
