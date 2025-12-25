package com.zonerental.config

import org.bukkit.World

/**
 * Data class representing all possible override settings for a region or group.
 * Nullable fields indicate "use default" - only non-null values are actual overrides.
 *
 * This consolidates 6 separate getter methods into a single type-safe container.
 */
data class RegionOverride(
    val price: Double? = null,
    val duration: Int? = null,
    val maxExtensions: Int? = null,
    val extensionPrice: Double? = null,
    val allowExtensions: Boolean? = null,
    val extensionDuration: Int? = null
) {
    /**
     * Checks if any override is set.
     */
    val hasAnyOverride: Boolean
        get() = price != null || duration != null || maxExtensions != null ||
                extensionPrice != null || allowExtensions != null || extensionDuration != null

    /**
     * Checks if this override is empty (no values set).
     */
    val isEmpty: Boolean
        get() = !hasAnyOverride

    /**
     * Merges this override with a fallback, using this override's values when present,
     * falling back to the other's values when this is null.
     *
     * Useful for: region.merge(group).merge(defaults)
     */
    fun merge(fallback: RegionOverride): RegionOverride = RegionOverride(
        price = this.price ?: fallback.price,
        duration = this.duration ?: fallback.duration,
        maxExtensions = this.maxExtensions ?: fallback.maxExtensions,
        extensionPrice = this.extensionPrice ?: fallback.extensionPrice,
        allowExtensions = this.allowExtensions ?: fallback.allowExtensions,
        extensionDuration = this.extensionDuration ?: fallback.extensionDuration
    )

    /**
     * Applies default values for any null fields.
     */
    fun withDefaults(defaults: RegionDefaults): ResolvedRegionConfig = ResolvedRegionConfig(
        price = price ?: defaults.price,
        duration = duration ?: defaults.duration,
        maxExtensions = maxExtensions ?: defaults.maxExtensions,
        extensionPrice = extensionPrice ?: defaults.extensionPrice,
        allowExtensions = allowExtensions ?: defaults.allowExtensions,
        extensionDuration = extensionDuration ?: defaults.extensionDuration
    )

    /**
     * Converts to a map for serialization (only non-null values).
     */
    fun toMap(): Map<String, Any> = buildMap {
        price?.let { put("price", it) }
        duration?.let { put("duration", it) }
        maxExtensions?.let { put("max-extensions", it) }
        extensionPrice?.let { put("extension-price", it) }
        allowExtensions?.let { put("allow-extensions", it) }
        extensionDuration?.let { put("extension-duration", it) }
    }

    companion object {
        /**
         * Empty override (all defaults).
         */
        val EMPTY = RegionOverride()

        /**
         * Creates a RegionOverride from a config map.
         */
        @JvmStatic
        fun fromMap(map: Map<String, Any?>): RegionOverride = RegionOverride(
            price = (map["price"] as? Number)?.toDouble(),
            duration = (map["duration"] as? Number)?.toInt(),
            maxExtensions = (map["max-extensions"] as? Number)?.toInt(),
            extensionPrice = (map["extension-price"] as? Number)?.toDouble(),
            allowExtensions = map["allow-extensions"] as? Boolean,
            extensionDuration = (map["extension-duration"] as? Number)?.toInt()
        )

        /**
         * Builder-style creation for Java interop.
         */
        @JvmStatic
        fun builder() = Builder()
    }

    /**
     * Builder for Java interop.
     */
    class Builder {
        private var price: Double? = null
        private var duration: Int? = null
        private var maxExtensions: Int? = null
        private var extensionPrice: Double? = null
        private var allowExtensions: Boolean? = null
        private var extensionDuration: Int? = null

        fun price(value: Double) = apply { price = value }
        fun duration(value: Int) = apply { duration = value }
        fun maxExtensions(value: Int) = apply { maxExtensions = value }
        fun extensionPrice(value: Double) = apply { extensionPrice = value }
        fun allowExtensions(value: Boolean) = apply { allowExtensions = value }
        fun extensionDuration(value: Int) = apply { extensionDuration = value }

        fun build() = RegionOverride(
            price, duration, maxExtensions, extensionPrice, allowExtensions, extensionDuration
        )
    }
}

/**
 * Default values from config.yml.
 */
data class RegionDefaults(
    val price: Double,
    val duration: Int,
    val maxExtensions: Int,
    val extensionPrice: Double,
    val allowExtensions: Boolean,
    val extensionDuration: Int
) {
    /**
     * Converts to a RegionOverride (all fields become non-null).
     */
    fun toOverride(): RegionOverride = RegionOverride(
        price = price,
        duration = duration,
        maxExtensions = maxExtensions,
        extensionPrice = extensionPrice,
        allowExtensions = allowExtensions,
        extensionDuration = extensionDuration
    )
}

/**
 * Fully resolved configuration with no nulls - ready for use.
 * Result of merging overrides with defaults.
 */
data class ResolvedRegionConfig(
    val price: Double,
    val duration: Int,
    val maxExtensions: Int,
    val extensionPrice: Double,
    val allowExtensions: Boolean,
    val extensionDuration: Int
) {
    /**
     * Checks if extensions are available (allowed and max > 0).
     */
    val extensionsAvailable: Boolean
        get() = allowExtensions && maxExtensions > 0

    /**
     * Calculates the actual extension price.
     * If extensionPrice is 0, auto-calculates from rental price and duration.
     */
    fun getActualExtensionPrice(extensionDays: Int = extensionDuration): Double {
        return if (extensionPrice <= 0) {
            // Auto-calculate: (rental price / rental duration) * extension duration
            (price / duration) * extensionDays
        } else {
            extensionPrice
        }
    }
}

/**
 * Represents a region target that can be either a specific region or a group.
 */
sealed class OverrideTarget {
    /**
     * A specific region in a specific world.
     */
    data class Region(
        val regionName: String,
        val world: World
    ) : OverrideTarget() {
        val compositeKey: String get() = "${world.name}:$regionName"
    }

    /**
     * A group of regions.
     */
    data class Group(val groupName: String) : OverrideTarget()

    companion object {
        /**
         * Parses a target string like "group:shops" or "world:region".
         */
        @JvmStatic
        fun parse(input: String, defaultWorld: World? = null): OverrideTarget? {
            return when {
                input.startsWith("group:") -> {
                    val groupName = input.substring(6)
                    if (groupName.isNotBlank()) Group(groupName) else null
                }
                input.contains(":") -> {
                    val parts = input.split(":", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        val world = org.bukkit.Bukkit.getWorld(parts[0])
                        if (world != null) Region(parts[1], world) else null
                    } else null
                }
                defaultWorld != null -> Region(input, defaultWorld)
                else -> null
            }
        }
    }
}
