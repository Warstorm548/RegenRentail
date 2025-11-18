package com.regionrental.managers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Rental {

    private final String regionName;
    private final UUID playerUUID;
    private final String playerName;
    private final long startDate;
    private long endDate;
    private int extensionCount;
    private double totalPaid;
    private double initialPrice; // Track initial rental price separately for refund calculations
    private double totalRefunded; // Track total amount refunded to prevent double-refunds
    private final List<RefundRecord> refundHistory; // Detailed refund history
    private final Set<String> warningSent;

    /**
     * Represents a single refund transaction for audit trail
     */
    public static class RefundRecord {
        private final double amount;
        private final long timestamp;
        private final String reason;
        private final String adminName;

        public RefundRecord(double amount, long timestamp, String reason, String adminName) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.reason = reason;
            this.adminName = adminName;
        }

        public double getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
        public String getAdminName() { return adminName; }

        public String getFormattedTimestamp() {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            );
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
            return dateTime.format(formatter);
        }
    }
    
    // Constructor for new rentals
    public Rental(String regionName, UUID playerUUID, String playerName, long endDate, double price) {
        this.regionName = regionName;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.startDate = System.currentTimeMillis();
        this.endDate = endDate;
        this.extensionCount = 0;
        this.totalPaid = price;
        this.initialPrice = price;
        this.totalRefunded = 0.0;
        this.refundHistory = new ArrayList<>();
        this.warningSent = new HashSet<>();
    }

    // Constructor for loading from storage (with refund data)
    public Rental(String regionName, UUID playerUUID, String playerName, long startDate, long endDate,
                  int extensionCount, double totalPaid, double initialPrice, double totalRefunded,
                  List<RefundRecord> refundHistory) {
        this.regionName = regionName;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.extensionCount = extensionCount;
        this.totalPaid = totalPaid;
        this.initialPrice = initialPrice;
        this.totalRefunded = totalRefunded;
        this.refundHistory = refundHistory != null ? refundHistory : new ArrayList<>();
        this.warningSent = new HashSet<>();
    }

    // Backward compatibility constructor (for old data without refund tracking)
    public Rental(String regionName, UUID playerUUID, String playerName, long startDate, long endDate,
                  int extensionCount, double totalPaid, double initialPrice) {
        this(regionName, playerUUID, playerName, startDate, endDate, extensionCount, totalPaid,
             initialPrice, 0.0, new ArrayList<>());
    }

    // Backward compatibility constructor (for old data without initialPrice or refund tracking)
    public Rental(String regionName, UUID playerUUID, String playerName, long startDate, long endDate,
                  int extensionCount, double totalPaid) {
        this(regionName, playerUUID, playerName, startDate, endDate, extensionCount, totalPaid,
             totalPaid, 0.0, new ArrayList<>());
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > endDate;
    }
    
    public long getTimeRemaining() {
        return endDate - System.currentTimeMillis();
    }
    
    public int getDaysRemaining() {
        long millis = getTimeRemaining();
        if (millis <= 0) return 0;
        return (int) (millis / (1000 * 60 * 60 * 24));
    }
    
    public int getHoursRemaining() {
        long millis = getTimeRemaining();
        if (millis <= 0) return 0;
        return (int) (millis / (1000 * 60 * 60));
    }
    
    public String getFormattedEndDate() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(endDate), 
            ZoneId.systemDefault()
        );
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        return dateTime.format(formatter);
    }
    
    public void extendRental(int days, double price) {
        this.endDate = this.endDate + (days * 24L * 60L * 60L * 1000L);
        this.extensionCount++;
        this.totalPaid += price;
        // Clear warnings when extended
        this.warningSent.clear();
    }
    
    public void resetTime(int days) {
        this.endDate = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L);
        this.warningSent.clear();
    }
    
    public boolean hasWarningBeenSent(String warningType) {
        return warningSent.contains(warningType);
    }
    
    public void markWarningSent(String warningType) {
        warningSent.add(warningType);
    }
    
    /**
     * Gets the total cost of extensions (excludes initial rental price)
     * @return The amount paid for extensions only
     */
    public double getExtensionCost() {
        return totalPaid - initialPrice;
    }

    /**
     * Records a refund transaction in the rental history
     * @param amount Amount refunded
     * @param reason Reason for refund (e.g., "duration_reset", "time_removal")
     * @param adminName Name of admin who issued refund
     */
    public void recordRefund(double amount, String reason, String adminName) {
        this.totalRefunded += amount;
        this.refundHistory.add(new RefundRecord(amount, System.currentTimeMillis(), reason, adminName));
    }

    /**
     * Gets the net amount that can still be refunded (prevents double-refunds)
     * @return The amount paid minus amount already refunded
     */
    public double getNetRefundableAmount() {
        return Math.max(0, totalPaid - totalRefunded);
    }

    /**
     * Adds time to rental with payment (for admin-charged additions)
     * Does NOT increment extension count (admin additions are separate)
     * @param days Days to add
     * @param price Amount charged
     */
    public void addTimeWithCharge(int days, double price) {
        this.endDate = this.endDate + (days * 24L * 60L * 60L * 1000L);
        this.totalPaid += price;
        // Note: extensionCount is NOT incremented for admin-charged additions
        this.warningSent.clear();
    }

    // Getters
    public String getRegionName() { return regionName; }
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public long getStartDate() { return startDate; }
    public long getEndDate() { return endDate; }
    public int getExtensionCount() { return extensionCount; }
    public double getTotalPaid() { return totalPaid; }
    public double getInitialPrice() { return initialPrice; }
    public double getTotalRefunded() { return totalRefunded; }
    public List<RefundRecord> getRefundHistory() { return new ArrayList<>(refundHistory); }

    // Setters (limited)
    public void setEndDate(long endDate) {
        this.endDate = endDate;
        this.warningSent.clear();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Rental rental = (Rental) obj;
        return regionName.equals(rental.regionName);
    }
    
    @Override
    public int hashCode() {
        return regionName.hashCode();
    }
}
