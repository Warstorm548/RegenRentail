package com.zonerental.async

/**
 * Represents a WorldGuard region's bounds for scanning.
 * Y-level aware to only scan the region's actual vertical extent.
 */
data class ScanRegion(
    val regionName: String,
    val worldName: String,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    val minZ: Int,
    val maxZ: Int
) {
    /** Width of region in blocks (X-axis) */
    val sizeX: Int get() = maxX - minX + 1

    /** Height of region in blocks (Y-axis) */
    val sizeY: Int get() = maxY - minY + 1

    /** Depth of region in blocks (Z-axis) */
    val sizeZ: Int get() = maxZ - minZ + 1

    /** Total number of blocks in the region */
    val totalBlocks: Long get() = sizeX.toLong() * sizeY * sizeZ

    /** Number of 16x16 chunk columns the region spans */
    val chunkCount: Int get() {
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)
    }

    /** Composite key for caching/storage */
    val compositeKey: String get() = "$worldName:$regionName"
}

/**
 * Represents a chunk coordinate with Y-level bounds from the region.
 */
data class ChunkCoord(
    val chunkX: Int,
    val chunkZ: Int,
    val minY: Int,
    val maxY: Int
)

/**
 * Represents a specific block coordinate found during scanning.
 */
data class BlockCoord(
    val x: Int,
    val y: Int,
    val z: Int
)

/**
 * A batch of chunks to process together.
 */
data class ChunkBatch(
    val chunks: List<ChunkCoord>,
    val batchIndex: Int,
    val totalBatches: Int
)

/**
 * Scan strategy based on region size.
 * Larger regions use smaller batches and longer delays to avoid TPS drops.
 */
sealed class ScanStrategy(
    val batchSize: Int,
    val delayBetweenBatchesMs: Long,
    val maxConcurrentChunks: Int
) {
    /** Tiny regions (<50 chunks) - aggressive scanning */
    data object Tiny : ScanStrategy(batchSize = 15, delayBetweenBatchesMs = 0L, maxConcurrentChunks = 4)

    /** Small regions (<200 chunks) - fast scanning */
    data object Small : ScanStrategy(batchSize = 10, delayBetweenBatchesMs = 25L, maxConcurrentChunks = 3)

    /** Medium regions (<500 chunks) - balanced */
    data object Medium : ScanStrategy(batchSize = 8, delayBetweenBatchesMs = 50L, maxConcurrentChunks = 2)

    /** Large regions (<1000 chunks) - conservative */
    data object Large : ScanStrategy(batchSize = 4, delayBetweenBatchesMs = 100L, maxConcurrentChunks = 1)

    /** Very large regions (<2000 chunks) - very conservative */
    data object VeryLarge : ScanStrategy(batchSize = 2, delayBetweenBatchesMs = 150L, maxConcurrentChunks = 1)

    /** Extreme regions (2000+ chunks) - maximum caution */
    data object Extreme : ScanStrategy(batchSize = 1, delayBetweenBatchesMs = 200L, maxConcurrentChunks = 1)

    companion object {
        /**
         * Determines the appropriate scan strategy based on chunk count.
         */
        fun fromChunkCount(chunkCount: Int): ScanStrategy = when {
            chunkCount < 50 -> Tiny
            chunkCount < 200 -> Small
            chunkCount < 500 -> Medium
            chunkCount < 1000 -> Large
            chunkCount < 2000 -> VeryLarge
            else -> Extreme
        }
    }
}

/**
 * TPS level thresholds for auto-throttling.
 */
enum class TpsLevel(
    val concurrencyMultiplier: Double,
    val delayMultiplier: Double
) {
    /** TPS >= 19.5 - Full speed */
    HEALTHY(1.0, 1.0),

    /** TPS >= 18.5 - Reduce concurrency, increase delays */
    WARNING(0.5, 2.0),

    /** TPS < 18.5 - Minimum concurrency, maximum delays */
    CRITICAL(0.25, 4.0)
}

/**
 * Adjusted strategy after TPS consideration.
 */
data class AdjustedStrategy(
    val batchSize: Int,
    val delayMs: Long,
    val shouldPause: Boolean
)

/**
 * Result of an async scan operation.
 */
sealed class ScanResult<out T> {
    data class Success<T>(val data: T) : ScanResult<T>()
    data class PartialSuccess<T>(val data: T, val failedChunks: List<ChunkCoord>) : ScanResult<T>()
    data class Failure(val error: String) : ScanResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is PartialSuccess -> data
        is Failure -> null
    }
}
