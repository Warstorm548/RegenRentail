package com.zonerental.config

import com.zonerental.ZoneRental
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.logging.Level

class SignsConfig(private val plugin: ZoneRental) {

    private val configFile: File = File(plugin.dataFolder, "signs.yml")
    private var config: YamlConfiguration

    // Dirty tracking for optimized saves
    @Volatile
    var isDirty: Boolean = false
        private set

    // Support block index for O(1) lookups: "world:x:y:z" -> compositeKey (world:region)
    private val supportBlockIndex = mutableMapOf<String, String>()

    init {
        createConfig()
        config = YamlConfiguration.loadConfiguration(configFile)
        migrateToWorldAwareKeys()
        rebuildSupportBlockIndex()
    }

    private fun createConfig() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            try {
                configFile.createNewFile()
                plugin.logger.info("Created signs.yml file")
            } catch (e: IOException) {
                plugin.logger.log(Level.SEVERE, "Could not create signs.yml!", e)
            }
        }
    }

    /**
     * Migrate signs from old format (region name only) to new format (world:region)
     * This runs automatically on plugin load and is idempotent (safe to run multiple times)
     */
    private fun migrateToWorldAwareKeys() {
        val signsSection = config.getConfigurationSection("signs") ?: return

        val migrations = mutableMapOf<String, Map<String, Any?>>()
        var migratedCount = 0

        // Find old format entries (no ":" in key)
        for (key in signsSection.getKeys(false)) {
            if (!key.contains(":")) {
                val world = config.getString("signs.$key.world")
                if (world.isNullOrEmpty()) {
                    plugin.logger.warning("Sign '$key' has no world field, skipping migration")
                    continue
                }

                val newKey = "$world:$key"
                val oldSection = config.getConfigurationSection("signs.$key")

                // Collect all data from old section
                oldSection?.let { section ->
                    val data = mutableMapOf<String, Any?>()
                    for (subKey in section.getKeys(true)) {
                        data[subKey] = section.get(subKey)
                    }
                    migrations[newKey] = data
                }

                migratedCount++
            }
        }

        // Apply migrations
        if (migrations.isNotEmpty()) {
            for ((compositeKey, data) in migrations) {
                for ((subKey, value) in data) {
                    config.set("signs.$compositeKey.$subKey", value)
                }
            }

            // Remove old keys (those without ":")
            for (key in signsSection.getKeys(false).toSet()) {
                if (!key.contains(":")) {
                    config.set("signs.$key", null)
                }
            }

            save()
            plugin.logger.info("Migrated $migratedCount sign(s) to world-aware format")
        }
    }

    private fun markDirty() {
        isDirty = true
    }

    /**
     * Saves only if there are unsaved changes
     */
    fun saveIfDirty() {
        if (isDirty) {
            save()
        }
    }

    fun save() {
        try {
            config.save(configFile)
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Could not save signs.yml!", e)
            return
        }
        isDirty = false
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        isDirty = false
        rebuildSupportBlockIndex()
    }

    /**
     * Builds the support block index from config data.
     * Called on load and reload for O(1) support block lookups.
     */
    fun rebuildSupportBlockIndex() {
        supportBlockIndex.clear()

        val signsSection = config.getConfigurationSection("signs") ?: return

        for (compositeKey in signsSection.getKeys(false)) {
            val path = "signs.$compositeKey.support-block"
            if (config.contains(path)) {
                val worldName = config.getString("signs.$compositeKey.world") ?: continue
                val x = config.getInt("$path.x")
                val y = config.getInt("$path.y")
                val z = config.getInt("$path.z")

                val locationKey = locationToKey(worldName, x, y, z)
                supportBlockIndex[locationKey] = compositeKey
            }
        }

        if (plugin.configManager?.isDebug == true) {
            plugin.logger.info("Built support block index with ${supportBlockIndex.size} entries")
        }
    }

    private fun locationToKey(worldName: String, x: Int, y: Int, z: Int): String =
        "$worldName:$x:$y:$z"

    private fun locationToKey(loc: Location): String =
        locationToKey(loc.world.name, loc.blockX, loc.blockY, loc.blockZ)

    /**
     * O(1) lookup to get composite key (world:region) for a support block location
     */
    fun getSupportBlockByLocation(location: Location): String? =
        supportBlockIndex[locationToKey(location)]

    fun addSign(region: String, location: Location) {
        val compositeKey = "${location.world.name}:$region"
        val path = "signs.$compositeKey"
        config.set("$path.world", location.world.name)
        config.set("$path.x", location.blockX)
        config.set("$path.y", location.blockY)
        config.set("$path.z", location.blockZ)
        markDirty()
    }

    fun addSupportBlock(region: String, world: World, supportLoc: Location, blockType: String, blockData: String) {
        val compositeKey = "${world.name}:$region"
        val path = "signs.$compositeKey.support-block"
        config.set("$path.x", supportLoc.blockX)
        config.set("$path.y", supportLoc.blockY)
        config.set("$path.z", supportLoc.blockZ)
        config.set("$path.original-type", blockType)
        config.set("$path.original-data", blockData)

        // Update index for O(1) lookups
        val locationKey = locationToKey(supportLoc)
        supportBlockIndex[locationKey] = compositeKey

        markDirty()
    }

    fun hasSupportBlock(region: String, world: World): Boolean {
        val compositeKey = "${world.name}:$region"
        return config.contains("signs.$compositeKey.support-block")
    }

    fun getSupportBlockLocation(region: String, world: World): Location? {
        if (!hasSupportBlock(region, world)) return null

        val compositeKey = "${world.name}:$region"
        val path = "signs.$compositeKey.support-block"
        val signLoc = getSignLocation(region, world) ?: return null

        val x = config.getInt("$path.x")
        val y = config.getInt("$path.y")
        val z = config.getInt("$path.z")

        return Location(signLoc.world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun getSupportBlockData(region: String, world: World): Map<String, String>? {
        if (!hasSupportBlock(region, world)) return null

        val compositeKey = "${world.name}:$region"
        val path = "signs.$compositeKey.support-block"

        return mapOf(
            "type" to (config.getString("$path.original-type") ?: "STONE"),
            "data" to (config.getString("$path.original-data") ?: "")
        )
    }

    fun removeSupportBlock(region: String, world: World) {
        val compositeKey = "${world.name}:$region"

        // Remove from index before clearing config
        getSupportBlockLocation(region, world)?.let { supportLoc ->
            supportBlockIndex.remove(locationToKey(supportLoc))
        }

        config.set("signs.$compositeKey.support-block", null)
        markDirty()
    }

    fun removeSign(region: String, world: World) {
        val compositeKey = "${world.name}:$region"

        // Remove support block from index before clearing config
        getSupportBlockLocation(region, world)?.let { supportLoc ->
            supportBlockIndex.remove(locationToKey(supportLoc))
        }

        config.set("signs.$compositeKey", null)
        markDirty()
    }

    fun hasSign(region: String, world: World): Boolean {
        val compositeKey = "${world.name}:$region"
        return config.contains("signs.$compositeKey")
    }

    fun getSignLocation(region: String, world: World): Location? {
        if (!hasSign(region, world)) return null

        val compositeKey = "${world.name}:$region"
        val path = "signs.$compositeKey"
        val worldName = config.getString("$path.world") ?: return null
        val x = config.getInt("$path.x")
        val y = config.getInt("$path.y")
        val z = config.getInt("$path.z")

        val resolvedWorld = plugin.server.getWorld(worldName) ?: return null
        return Location(resolvedWorld, x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun getAllSigns(): Map<String, Location> {
        val signs = mutableMapOf<String, Location>()

        val signsSection = config.getConfigurationSection("signs") ?: return signs

        for (compositeKey in signsSection.getKeys(false)) {
            val path = "signs.$compositeKey"
            val worldName = config.getString("$path.world") ?: continue
            val x = config.getInt("$path.x")
            val y = config.getInt("$path.y")
            val z = config.getInt("$path.z")

            val world = plugin.server.getWorld(worldName)
            if (world != null) {
                signs[compositeKey] = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
            }
        }

        return signs
    }

    /**
     * Get composite key (world:region) by sign location
     */
    fun getRegionByLocation(location: Location): String? {
        for ((compositeKey, signLoc) in getAllSigns()) {
            if (signLoc.blockX == location.blockX &&
                signLoc.blockY == location.blockY &&
                signLoc.blockZ == location.blockZ &&
                signLoc.world.name == location.world.name) {
                return compositeKey
            }
        }
        return null
    }
}
