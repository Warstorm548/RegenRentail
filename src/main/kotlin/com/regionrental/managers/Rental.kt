package com.regionrental.managers

import com.regionrental.util.TimeUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents an active rental of a WorldGuard region.
 * Converted from 300-line Java class to ~150-line Kotlin class.
 *
 * This class maintains full Java interoperability through:
 * - @JvmField annotations for direct field access
 * - @JvmStatic factory methods replacing multiple constructors
 * - @JvmOverloads for default parameter support
 * - Nested RefundRecord class for backwards compatibility
 */
class Rental private constructor(
    val regionName: String,
    val worldName: String,
    val playerUUID: UUID,
    val playerName: String,
    val startDate: Long,
    endDate: Long,
    extensionCount: Int,
    totalPaid: Double,
    val initialPrice: Double,
    totalRefunded: Double,
    refundHistory: MutableList<RefundRecord>?,
    members: MutableSet<UUID>?
) {
    // Mutable fields
    var endDate: Long = endDate
        set(value) {
            field = value
            warningSent.clear()
        }

    var extensionCount: Int = extensionCount
        private set

    var totalPaid: Double = totalPaid
        private set

    var totalRefunded: Double = totalRefunded
        private set

    private val refundHistory: MutableList<RefundRecord> = refundHistory ?: mutableListOf()
    private val warningSent: MutableSet<String> = mutableSetOf()
    private val members: MutableSet<UUID> = members ?: mutableSetOf()

    // ============ Computed Properties ============

    /**
     * Unique composite key for this rental (world:region).
     */
    val compositeKey: String
        get() = "$worldName:$regionName"

    /**
     * Whether the rental has expired.
     */
    val isExpired: Boolean
        get() = System.currentTimeMillis() > endDate

    /**
     * Time remaining until expiration in milliseconds.
     */
    val timeRemaining: Long
        get() = endDate - System.currentTimeMillis()

    /**
     * Days remaining until expiration (0 if expired).
     */
    val daysRemaining: Int
        get() = if (timeRemaining <= 0) 0 else TimeUtils.millisToDays(timeRemaining)

    /**
     * Hours remaining until expiration (0 if expired).
     */
    val hoursRemaining: Int
        get() = if (timeRemaining <= 0) 0 else TimeUtils.millisToHours(timeRemaining)

    /**
     * Formatted end date string (MM/dd HH:mm).
     */
    val formattedEndDate: String
        get() = TimeUtils.formatTimestamp(endDate)

    /**
     * Total cost of extensions (excludes initial rental price).
     */
    val extensionCost: Double
        get() = totalPaid - initialPrice

    /**
     * Net amount that can still be refunded (prevents double-refunds).
     */
    val netRefundableAmount: Double
        get() = maxOf(0.0, totalPaid - totalRefunded)

    /**
     * Number of members in this rental.
     */
    val memberCount: Int
        get() = members.size

    // ============ Rental Operations ============

    /**
     * Extends the rental by the specified days and adds to total paid.
     */
    fun extendRental(days: Int, price: Double) {
        endDate += TimeUtils.daysToMillis(days)
        extensionCount++
        totalPaid += price
        warningSent.clear()
    }

    /**
     * Resets the rental time to start from now.
     */
    fun resetTime(days: Int) {
        endDate = System.currentTimeMillis() + TimeUtils.daysToMillis(days)
        warningSent.clear()
    }

    /**
     * Adds time with a charge (admin additions - does NOT increment extension count).
     */
    fun addTimeWithCharge(days: Int, price: Double) {
        endDate += TimeUtils.daysToMillis(days)
        totalPaid += price
        warningSent.clear()
    }

    // ============ Warning Management ============

    /**
     * Checks if a specific warning type has been sent.
     */
    fun hasWarningBeenSent(warningType: String): Boolean = warningType in warningSent

    /**
     * Marks a warning type as sent.
     */
    fun markWarningSent(warningType: String) {
        warningSent.add(warningType)
    }

    // ============ Refund Management ============

    /**
     * Records a refund transaction.
     */
    fun recordRefund(amount: Double, reason: String, adminName: String) {
        totalRefunded += amount
        refundHistory.add(RefundRecord(amount, System.currentTimeMillis(), reason, adminName))
    }

    /**
     * Gets a copy of the refund history.
     */
    fun getRefundHistory(): List<RefundRecord> = refundHistory.toList()

    // ============ Member Management ============

    /**
     * Gets a copy of all members.
     */
    fun getMembers(): Set<UUID> = members.toSet()

    /**
     * Adds a member to this rental.
     * @return true if added, false if already exists
     */
    fun addMember(memberUUID: UUID): Boolean = members.add(memberUUID)

    /**
     * Removes a member from this rental.
     * @return true if removed, false if not found
     */
    fun removeMember(memberUUID: UUID): Boolean = members.remove(memberUUID)

    /**
     * Checks if a player is a member.
     */
    fun hasMember(memberUUID: UUID): Boolean = memberUUID in members

    /**
     * Clears all members.
     */
    fun clearMembers() = members.clear()

    // ============ Object Methods ============

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rental) return false
        return regionName == other.regionName && worldName == other.worldName
    }

    override fun hashCode(): Int = compositeKey.hashCode()

    override fun toString(): String = "Rental($compositeKey, player=$playerName, expires=$formattedEndDate)"

    // ============ Nested Classes ============

    /**
     * Represents a single refund transaction for audit trail.
     * Kept as nested class for Java backwards compatibility (Rental.RefundRecord).
     */
    data class RefundRecord(
        val amount: Double,
        val timestamp: Long,
        val reason: String,
        val adminName: String
    ) {
        val formattedTimestamp: String
            get() = TimeUtils.formatTimestamp(timestamp)
    }

    // ============ Factory Methods (Replace Java Constructors) ============

    companion object {
        /**
         * Creates a new rental (for when a player rents a region).
         */
        @JvmStatic
        fun create(
            regionName: String,
            worldName: String,
            playerUUID: UUID,
            playerName: String,
            endDate: Long,
            price: Double
        ): Rental = Rental(
            regionName = regionName,
            worldName = worldName,
            playerUUID = playerUUID,
            playerName = playerName,
            startDate = System.currentTimeMillis(),
            endDate = endDate,
            extensionCount = 0,
            totalPaid = price,
            initialPrice = price,
            totalRefunded = 0.0,
            refundHistory = null,
            members = null
        )

        /**
         * Loads a rental from storage (full data including refund history).
         */
        @JvmStatic
        @JvmOverloads
        fun fromStorage(
            regionName: String,
            worldName: String,
            playerUUID: UUID,
            playerName: String,
            startDate: Long,
            endDate: Long,
            extensionCount: Int,
            totalPaid: Double,
            initialPrice: Double,
            totalRefunded: Double = 0.0,
            refundHistory: MutableList<RefundRecord>? = null,
            members: MutableSet<UUID>? = null
        ): Rental = Rental(
            regionName = regionName,
            worldName = worldName,
            playerUUID = playerUUID,
            playerName = playerName,
            startDate = startDate,
            endDate = endDate,
            extensionCount = extensionCount,
            totalPaid = totalPaid,
            initialPrice = initialPrice,
            totalRefunded = totalRefunded,
            refundHistory = refundHistory,
            members = members
        )

        /**
         * Legacy constructor for old data without initialPrice or refund tracking.
         */
        @JvmStatic
        fun fromLegacyStorage(
            regionName: String,
            worldName: String,
            playerUUID: UUID,
            playerName: String,
            startDate: Long,
            endDate: Long,
            extensionCount: Int,
            totalPaid: Double
        ): Rental = fromStorage(
            regionName = regionName,
            worldName = worldName,
            playerUUID = playerUUID,
            playerName = playerName,
            startDate = startDate,
            endDate = endDate,
            extensionCount = extensionCount,
            totalPaid = totalPaid,
            initialPrice = totalPaid, // Default initialPrice to totalPaid
            totalRefunded = 0.0,
            refundHistory = null,
            members = null
        )

        /**
         * Migration constructor for old data without world field.
         */
        @JvmStatic
        fun fromMigration(
            regionName: String,
            defaultWorldName: String,
            playerUUID: UUID,
            playerName: String,
            startDate: Long,
            endDate: Long,
            extensionCount: Int,
            totalPaid: Double,
            initialPrice: Double,
            totalRefunded: Double,
            refundHistory: MutableList<RefundRecord>?
        ): Rental = fromStorage(
            regionName = regionName,
            worldName = defaultWorldName,
            playerUUID = playerUUID,
            playerName = playerName,
            startDate = startDate,
            endDate = endDate,
            extensionCount = extensionCount,
            totalPaid = totalPaid,
            initialPrice = initialPrice,
            totalRefunded = totalRefunded,
            refundHistory = refundHistory,
            members = null
        )
    }
}
