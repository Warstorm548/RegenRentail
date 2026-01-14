package com.zonerental.managers

import com.zonerental.ZoneRental
import com.zonerental.extensions.toComponent
import com.zonerental.util.WorldRegionParser
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign

/**
 * Manages rental signs for regions.
 */
class SignManager(private val plugin: ZoneRental) {

    private val dirtySigns = mutableSetOf<String>() // Track signs that need updates

    fun loadAllSigns() {
        // Migrate existing signs to include support block data
        migrateSupportBlocks()

        // Mark all signs as dirty to update them on startup
        markAllSignsDirty()

        // Update all dirty signs
        updateAllSigns()
    }

    /**
     * Marks all signs as dirty for initial update or full refresh.
     */
    private fun markAllSignsDirty() {
        val signs = plugin.signsConfig.getAllSigns()
        dirtySigns.addAll(signs.keys)
    }

    /**
     * Migrates existing signs to include support block data.
     * This is for backward compatibility with signs created before this feature.
     */
    private fun migrateSupportBlocks() {
        val signs = plugin.signsConfig.getAllSigns()
        var migratedCount = 0

        for ((compositeKey, signLoc) in signs) {
            // Parse composite key "world:region"
            val worldName = WorldRegionParser.extractWorldName(compositeKey)
            val regionName = WorldRegionParser.extractRegionName(compositeKey)

            if (worldName == null || regionName == null) {
                plugin.logger.warning("Invalid composite key format: $compositeKey")
                continue
            }

            val world = plugin.server.getWorld(worldName)
            if (world == null) {
                plugin.logger.warning("World not found for sign: $worldName")
                continue
            }

            // Skip if support block already exists
            if (plugin.signsConfig.hasSupportBlock(regionName, world)) {
                continue
            }

            val signBlock = signLoc.getBlock()

            if (signBlock.state !is Sign) {
                plugin.logger.warning("Sign for region $compositeKey no longer exists at location")
                continue
            }

            // Detect support block
            val supportBlock = getSupportBlock(signBlock)
            if (supportBlock != null) {
                // Store the support block information
                val supportLoc = supportBlock.location
                val blockType = supportBlock.type.name
                val blockData = supportBlock.blockData.asString

                plugin.signsConfig.addSupportBlock(regionName, world, supportLoc, blockType, blockData)
                migratedCount++

                plugin.logger.info("Migrated support block for region $compositeKey (Type: $blockType)")
            } else {
                plugin.logger.warning("Could not detect support block for existing sign in region $compositeKey")
            }
        }

        if (migratedCount > 0) {
            plugin.logger.info("Migrated $migratedCount sign(s) with support block protection")
        }
    }

    fun createSign(regionName: String, location: Location) {
        // Store sign location (addSign uses location's world automatically)
        plugin.signsConfig.addSign(regionName, location)

        // Detect and store support block
        val signBlock = location.getBlock()
        val supportBlock = getSupportBlock(signBlock)

        if (supportBlock != null) {
            // Store the support block information
            val supportLoc = supportBlock.location
            val blockType = supportBlock.type.name
            val blockData = supportBlock.blockData.asString

            plugin.signsConfig.addSupportBlock(regionName, location.world, supportLoc, blockType, blockData)
            plugin.logger.info(
                "Stored support block for region ${location.world.name}:$regionName " +
                "(Type: $blockType at ${supportLoc.blockX},${supportLoc.blockY},${supportLoc.blockZ})"
            )
        } else {
            plugin.logger.warning("Could not detect support block for sign at $location")
        }

        // Update the sign
        updateSign(regionName, location.world)

        plugin.logger.info("Created rental sign for region ${location.world.name}:$regionName")
    }

    /**
     * Gets the support block for a sign (the block it's attached to or placed on).
     */
    private fun getSupportBlock(signBlock: Block): Block? {
        val blockData = signBlock.blockData

        // Check if it's a wall sign
        if (blockData is WallSign) {
            val facing = blockData.facing
            // Wall signs are attached to the block opposite to their facing direction
            return signBlock.getRelative(facing.oppositeFace)
        }

        // Otherwise it's a standing sign - support block is below
        if (signBlock.type.name.contains("SIGN")) {
            return signBlock.getRelative(BlockFace.DOWN)
        }

        return null
    }

    fun removeSign(regionName: String, world: World) {
        plugin.signsConfig.removeSign(regionName, world)
        plugin.logger.info("Removed rental sign for region ${world.name}:$regionName")
    }

    /**
     * Completely removes ZoneRental setup from a region.
     * This includes removing the sign from config and restoring the support block.
     *
     * @return true if sign was removed, false if no sign existed
     */
    fun removeRegionSetup(regionName: String, world: World): Boolean {
        val location = plugin.signsConfig.getSignLocation(regionName, world) ?: return false

        // Restore the support block if it exists
        if (plugin.signsConfig.hasSupportBlock(regionName, world)) {
            val supportLoc = plugin.signsConfig.getSupportBlockLocation(regionName, world)
            val supportData = plugin.signsConfig.getSupportBlockData(regionName, world)

            if (supportLoc != null && supportData != null) {
                try {
                    val supportBlock = supportLoc.getBlock()
                    val originalType = Material.valueOf(supportData["type"] ?: "")
                    val originalData = supportData["data"]

                    // Restore the block type
                    supportBlock.type = originalType

                    // Restore the block data if it exists
                    if (!originalData.isNullOrEmpty()) {
                        try {
                            val blockData = plugin.server.createBlockData(originalData)
                            supportBlock.blockData = blockData
                        } catch (e: IllegalArgumentException) {
                            plugin.logger.warning("Could not restore block data for support block: ${e.message}")
                        }
                    }

                    plugin.logger.info(
                        "Restored support block for region ${world.name}:$regionName to ${originalType.name}"
                    )
                } catch (e: IllegalArgumentException) {
                    plugin.logger.warning(
                        "Could not restore support block for region ${world.name}:$regionName: " +
                        "Invalid material type - ${e.message}"
                    )
                }
            }
        } else {
            // No support block data - manually break the sign
            val block = location.getBlock()
            if (block.state is Sign) {
                block.type = Material.AIR
            }
        }

        // Remove from config (this removes both sign and support block data)
        plugin.signsConfig.removeSign(regionName, world)
        plugin.logger.info("Removed ZoneRental setup from region ${world.name}:$regionName")

        return true
    }

    fun updateSign(regionName: String, world: World) {
        val location = plugin.signsConfig.getSignLocation(regionName, world) ?: return

        val block = location.getBlock()

        // Check if block is a sign
        val sign = block.state as? Sign
        if (sign == null) {
            plugin.logger.warning("Sign location for ${world.name}:$regionName is not a sign!")
            return
        }

        val rental = plugin.rentalManager.getRental(regionName, world)

        if (rental == null) {
            // Region is available
            updateAvailableSign(sign, regionName)
        } else {
            // Region is rented
            updateRentedSign(sign, rental)
        }

        sign.update(true)
    }

    private fun updateAvailableSign(sign: Sign, regionName: String) {
        val world = sign.world
        val price = plugin.configManager.getPriceForRegion(regionName, world)
        val duration = plugin.configManager.getDurationForRegion(regionName, world)
        val formattedPrice = String.format(plugin.configManager.currencyFormat, price)

        val formatList = plugin.configManager.availableSignFormat

        // Null/empty check to prevent NPE
        if (formatList.isNullOrEmpty()) {
            plugin.logger.warning("Available sign format is null or empty for region $regionName")
            return
        }

        var line = 0
        for (format in formatList) {
            if (line >= 4) break

            val text = format
                .replace("{region}", regionName)
                .replace("{price}", formattedPrice)
                .replace("{duration}", duration.toString())

            sign.line(line, text.toComponent())
            line++
        }
    }

    private fun updateRentedSign(sign: Sign, rental: Rental) {
        val formatList = plugin.configManager.rentedSignFormat

        // Null/empty check to prevent NPE
        if (formatList.isNullOrEmpty()) {
            plugin.logger.warning("Rented sign format is null or empty for region ${rental.regionName}")
            return
        }

        var line = 0
        for (format in formatList) {
            if (line >= 4) break

            val text = format
                .replace("{region}", rental.regionName)
                .replace("{owner}", rental.playerName)
                .replace("{expires}", rental.formattedEndDate)
                .replace("{days}", rental.daysRemaining.toString())
                .replace("{hours}", rental.hoursRemaining.toString())

            sign.line(line, text.toComponent())
            line++
        }
    }

    /**
     * Marks a sign as dirty (needs update).
     * Uses composite key format "world:region".
     */
    fun markSignDirty(regionName: String, world: World) {
        val compositeKey = "${world.name}:$regionName"
        dirtySigns.add(compositeKey)
    }

    /**
     * Marks multiple signs as dirty using composite keys (world:region format).
     * Used for bulk operations like group overrides.
     */
    fun bulkMarkSignsDirty(compositeKeys: List<String>) {
        dirtySigns.addAll(compositeKeys)
    }

    /**
     * Updates all dirty signs and clears the dirty set.
     * This is much more efficient than updating ALL signs every 30 seconds.
     */
    fun updateAllSigns() {
        if (dirtySigns.isEmpty()) {
            return // Nothing to update
        }

        val allSigns = plugin.signsConfig.getAllSigns()

        // Update only dirty signs
        for (compositeKey in dirtySigns) {
            val location = allSigns[compositeKey] ?: continue // Sign was removed

            // Parse composite key "world:region"
            val regionName = WorldRegionParser.extractRegionName(compositeKey) ?: continue
            val world = location.world ?: continue

            updateSign(regionName, world)
        }

        // Clear dirty set after updating
        dirtySigns.clear()
    }

    fun isRentalSign(location: Location): Boolean {
        return plugin.signsConfig.getRegionByLocation(location) != null
    }

    fun getRegionFromSign(location: Location): String? {
        return plugin.signsConfig.getRegionByLocation(location)
    }

    /**
     * Gets the region name if the given location is a protected support block.
     * Uses O(1) index lookup instead of O(n) linear scan for better performance.
     *
     * @return Composite key "world:region" if this is a support block, null otherwise
     */
    fun getSupportBlockRegion(location: Location): String? {
        // O(1) lookup via support block index
        return plugin.signsConfig.getSupportBlockByLocation(location)
    }
}
