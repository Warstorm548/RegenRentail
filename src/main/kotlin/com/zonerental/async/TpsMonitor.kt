package com.zonerental.async

import com.zonerental.ZoneRental
import org.bukkit.Bukkit
import kotlin.math.roundToInt

/**
 * Monitors server TPS and adjusts scan strategies accordingly.
 * Uses Paper's TPS API to determine server load.
 */
class TpsMonitor(private val plugin: ZoneRental) {

    private val config get() = plugin.configManager

    /**
     * Gets the current server TPS (1-minute average).
     * Uses Paper's TPS API, falls back to 20.0 if unavailable.
     */
    fun getCurrentTps(): Double {
        return try {
            // Paper API: Bukkit.getTPS() returns array [1min, 5min, 15min]
            val tps = Bukkit.getTPS()
            tps.getOrElse(0) { 20.0 }.coerceIn(0.0, 20.0)
        } catch (e: Exception) {
            // Fallback if TPS API unavailable (shouldn't happen on Paper)
            20.0
        }
    }

    /**
     * Gets the current TPS level based on configured thresholds.
     */
    fun getTpsLevel(): TpsLevel {
        val tps = getCurrentTps()
        val healthyThreshold = config.tpsHealthyThreshold
        val warningThreshold = config.tpsWarningThreshold

        return when {
            tps >= healthyThreshold -> TpsLevel.HEALTHY
            tps >= warningThreshold -> TpsLevel.WARNING
            else -> TpsLevel.CRITICAL
        }
    }

    /**
     * Adjusts a scan strategy based on current TPS.
     * Reduces batch sizes and increases delays when TPS is low.
     */
    fun adjustStrategy(baseStrategy: ScanStrategy): AdjustedStrategy {
        val tpsLevel = getTpsLevel()
        val currentTps = getCurrentTps()

        val adjustedBatchSize = (baseStrategy.batchSize * tpsLevel.concurrencyMultiplier)
            .roundToInt()
            .coerceAtLeast(1)

        val adjustedDelay = (baseStrategy.delayBetweenBatchesMs * tpsLevel.delayMultiplier).toLong()

        // Pause if TPS is critically low
        val shouldPause = tpsLevel == TpsLevel.CRITICAL && currentTps < 18.0

        return AdjustedStrategy(
            batchSize = adjustedBatchSize,
            delayMs = adjustedDelay,
            shouldPause = shouldPause
        )
    }

    /**
     * Checks if async scanning should be paused due to low TPS.
     */
    fun shouldPauseScan(): Boolean {
        return getCurrentTps() < 18.0
    }

    /**
     * Logs TPS status for debugging.
     */
    fun logTpsStatus(regionName: String) {
        if (config.isDebugAsync) {
            val tps = getCurrentTps()
            val level = getTpsLevel()
            plugin.logger.info(
                "[$regionName] TPS: %.2f (%s)".format(tps, level.name)
            )
        }
    }

    /**
     * Estimates scan time based on chunk count and current TPS.
     */
    fun estimateScanTime(chunkCount: Int): Long {
        val strategy = ScanStrategy.fromChunkCount(chunkCount)
        val adjusted = adjustStrategy(strategy)

        // Rough estimate: time per batch + delay between batches
        val batchCount = (chunkCount + adjusted.batchSize - 1) / adjusted.batchSize
        val scanTimePerBatch = 50L // ~50ms per chunk batch
        val totalDelays = (batchCount - 1) * adjusted.delayMs

        return (batchCount * scanTimePerBatch) + totalDelays
    }
}
