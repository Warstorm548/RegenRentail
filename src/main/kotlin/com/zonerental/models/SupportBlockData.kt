package com.zonerental.models

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.Bukkit

/**
 * Represents data about a support block that a sign is attached to or placed on.
 * This data is stored to restore the original block if the sign is removed.
 */
data class SupportBlockData(
    val type: Material,
    val blockData: String,
    val location: Location
) {
    /**
     * Gets the BlockData object from the stored string.
     */
    val parsedBlockData: BlockData?
        get() = try {
            if (blockData.isNotBlank()) {
                Bukkit.createBlockData(blockData)
            } else {
                type.createBlockData()
            }
        } catch (e: Exception) {
            type.createBlockData()
        }

    /**
     * Restores this block data to the world.
     */
    fun restore() {
        val block = location.block
        block.type = type
        parsedBlockData?.let { block.blockData = it }
    }

    /**
     * Checks if the block at this location still matches the stored data.
     */
    fun matches(block: Block): Boolean =
        block.type == type && block.location == location

    companion object {
        /**
         * Captures the current state of a block.
         */
        @JvmStatic
        fun capture(block: Block): SupportBlockData =
            SupportBlockData(
                type = block.type,
                blockData = block.blockData.asString,
                location = block.location.clone()
            )

        /**
         * Captures the current state of a block at a location.
         */
        @JvmStatic
        fun capture(location: Location): SupportBlockData =
            capture(location.block)

        /**
         * Creates SupportBlockData from stored config values.
         *
         * @param type The material type as a string
         * @param blockData The block data string
         * @param location The location of the block
         */
        @JvmStatic
        fun fromConfig(type: String, blockData: String, location: Location): SupportBlockData? {
            val material = Material.matchMaterial(type) ?: return null
            return SupportBlockData(material, blockData, location)
        }

        /**
         * Creates SupportBlockData from a map (for deserialization).
         */
        @JvmStatic
        fun fromMap(map: Map<String, Any>, location: Location): SupportBlockData? {
            val typeStr = map["type"] as? String ?: return null
            val material = Material.matchMaterial(typeStr) ?: return null
            val blockData = map["data"] as? String ?: ""
            return SupportBlockData(material, blockData, location)
        }
    }

    /**
     * Converts this SupportBlockData to a map for serialization.
     */
    fun toMap(): Map<String, String> = mapOf(
        "type" to type.name,
        "data" to blockData
    )
}
