package com.regionrental.managers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
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
    private final Set<String> warningSent;
    
    // Constructor for new rentals
    public Rental(String regionName, UUID playerUUID, String playerName, long endDate, double price) {
        this.regionName = regionName;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.startDate = System.currentTimeMillis();
        this.endDate = endDate;
        this.extensionCount = 0;
        this.totalPaid = price;
        this.initialPrice = price; // Store initial price
        this.warningSent = new HashSet<>();
    }
    
    // Constructor for loading from storage
    public Rental(String regionName, UUID playerUUID, String playerName, long startDate, long endDate,
                  int extensionCount, double totalPaid, double initialPrice) {
        this.regionName = regionName;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.extensionCount = extensionCount;
        this.totalPaid = totalPaid;
        this.initialPrice = initialPrice;
        this.warningSent = new HashSet<>();
    }

    // Backward compatibility constructor (for old data without initialPrice)
    public Rental(String regionName, UUID playerUUID, String playerName, long startDate, long endDate,
                  int extensionCount, double totalPaid) {
        this(regionName, playerUUID, playerName, startDate, endDate, extensionCount, totalPaid, totalPaid);
        // If no initialPrice stored, assume all of totalPaid is initial (no extensions tracked)
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

    // Getters
    public String getRegionName() { return regionName; }
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public long getStartDate() { return startDate; }
    public long getEndDate() { return endDate; }
    public int getExtensionCount() { return extensionCount; }
    public double getTotalPaid() { return totalPaid; }
    public double getInitialPrice() { return initialPrice; }
    
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
