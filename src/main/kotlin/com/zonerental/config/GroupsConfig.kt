package com.zonerental.config

import com.zonerental.ZoneRental
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

/**
 * Manages region groups configuration in groups.yml
 * Groups allow applying override settings to multiple regions at once
 */
class GroupsConfig(private val plugin: ZoneRental) {

    private val configFile: File = File(plugin.dataFolder, "groups.yml")
    private var config: YamlConfiguration

    @Volatile
    var isDirty: Boolean = false
        private set

    companion object {
        // Reserved group names that cannot be used
        private val RESERVED_NAMES = setOf("all", "none", "default")

        // Group name validation pattern: alphanumeric and underscore, 2-30 characters
        private val GROUP_NAME_PATTERN = Regex("^[a-zA-Z0-9_]{2,30}$")
    }

    init {
        createConfig()
        config = YamlConfiguration.loadConfiguration(configFile)
        initializeConfig()
    }

    private fun createConfig() {
        if (!configFile.exists()) {
            try {
                configFile.parentFile?.mkdirs()
                configFile.createNewFile()
                plugin.logger.info("Created groups.yml configuration file")
            } catch (e: IOException) {
                plugin.logger.severe("Could not create groups.yml: ${e.message}")
            }
        }
    }

    private fun initializeConfig() {
        // Add header comments on first creation
        if (!config.contains("groups")) {
            config.options().setHeader(listOf(
                "ZoneRental - Region Groups Configuration",
                "Use /zrgroup commands to manage groups",
                "Groups allow applying override settings to multiple regions at once",
                "",
                "Format: Regions stored as \"world:region\" composite keys",
                "",
                "Commands:",
                "  /zrgroup create <group_name> [regions]     # Create group",
                "  /zrgroup edit <group_name> add [regions]   # Add regions",
                "  /zrgroup edit <group_name> remove [regions]# Remove regions",
                "  /zrgroup delete <group_name>               # Delete group",
                "  /zrgroup list                              # List all groups",
                "  /zrgroup view <group_name>                 # View group details"
            ))
            config.createSection("groups")
            save()
        }
    }

    private fun markDirty() {
        isDirty = true
    }

    fun saveIfDirty() {
        if (isDirty) save()
    }

    fun save() {
        try {
            config.save(configFile)
        } catch (e: IOException) {
            plugin.logger.severe("Could not save groups.yml: ${e.message}")
            return
        }
        isDirty = false
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        isDirty = false
    }

    // ========== CRUD Operations ==========

    /**
     * Creates a new group with the specified regions
     */
    fun createGroup(groupName: String, compositeKeys: List<String>): Boolean {
        if (groupExists(groupName)) return false

        config.set("groups.$groupName.regions", compositeKeys)
        markDirty()

        plugin.logger.info("Created group '$groupName' with ${compositeKeys.size} regions")
        return true
    }

    /**
     * Deletes a group
     */
    fun deleteGroup(groupName: String): Boolean {
        if (!groupExists(groupName)) return false

        config.set("groups.$groupName", null)
        markDirty()

        plugin.logger.info("Deleted group '$groupName'")
        return true
    }

    /**
     * Adds regions to an existing group
     */
    fun addRegionsToGroup(groupName: String, compositeKeys: List<String>): Boolean {
        if (!groupExists(groupName)) return false

        val currentRegions = getGroupRegions(groupName).toMutableList()

        // Add new regions (avoiding duplicates)
        for (key in compositeKeys) {
            if (key !in currentRegions) {
                currentRegions.add(key)
            }
        }

        config.set("groups.$groupName.regions", currentRegions)
        markDirty()

        plugin.logger.info("Added ${compositeKeys.size} regions to group '$groupName'")
        return true
    }

    /**
     * Removes regions from a group
     */
    fun removeRegionsFromGroup(groupName: String, compositeKeys: List<String>): Boolean {
        if (!groupExists(groupName)) return false

        val currentRegions = getGroupRegions(groupName).toMutableList()
        currentRegions.removeAll(compositeKeys.toSet())

        config.set("groups.$groupName.regions", currentRegions)
        markDirty()

        plugin.logger.info("Removed ${compositeKeys.size} regions from group '$groupName'")
        return true
    }

    // ========== Query Methods ==========

    /**
     * Gets all regions in a group
     */
    fun getGroupRegions(groupName: String): List<String> =
        config.getStringList("groups.$groupName.regions").toMutableList()

    /**
     * Gets all group names
     */
    val allGroups: Set<String>
        get() = config.getConfigurationSection("groups")?.getKeys(false) ?: emptySet()

    /**
     * Checks if a group exists
     */
    fun groupExists(groupName: String): Boolean =
        config.contains("groups.$groupName")

    /**
     * Gets the number of regions in a group
     */
    fun getGroupSize(groupName: String): Int =
        getGroupRegions(groupName).size

    /**
     * Gets total number of groups
     */
    val groupCount: Int
        get() = allGroups.size

    // ========== Membership Tracking ==========

    /**
     * Checks if a region is in any group
     */
    fun isRegionInGroup(compositeKey: String): Boolean =
        allGroups.any { compositeKey in getGroupRegions(it) }

    /**
     * Gets the group a region belongs to
     */
    fun getRegionGroup(compositeKey: String): String? =
        allGroups.firstOrNull { compositeKey in getGroupRegions(it) }

    /**
     * Checks if multiple regions are in any group
     * Returns map of composite key → group name for regions that are grouped
     */
    fun checkGroupMembership(compositeKeys: List<String>): Map<String, String> =
        compositeKeys.mapNotNull { key ->
            getRegionGroup(key)?.let { group -> key to group }
        }.toMap()

    // ========== Validation ==========

    /**
     * Validates a group name
     */
    fun isValidGroupName(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        if (!GROUP_NAME_PATTERN.matches(name)) return false
        return name.lowercase() !in RESERVED_NAMES
    }

    /**
     * Gets validation error message for a group name
     */
    fun getValidationError(name: String?): String? = when {
        name.isNullOrEmpty() -> "Group name cannot be empty"
        name.length < 2 -> "Group name must be at least 2 characters"
        name.length > 30 -> "Group name must be 30 characters or less"
        !GROUP_NAME_PATTERN.matches(name) -> "Group name can only contain letters, numbers, and underscores"
        name.lowercase() in RESERVED_NAMES -> "Group name '$name' is reserved and cannot be used"
        else -> null // Valid
    }

    // ========== Utility Methods ==========

    /**
     * Gets statistics about all groups
     */
    fun getStatistics(): Map<String, Any?> {
        var totalRegions = 0
        var largestGroupSize = 0
        var largestGroup: String? = null

        for (groupName in allGroups) {
            val size = getGroupSize(groupName)
            totalRegions += size

            if (size > largestGroupSize) {
                largestGroupSize = size
                largestGroup = groupName
            }
        }

        return mapOf(
            "group_count" to allGroups.size,
            "total_regions" to totalRegions,
            "largest_group" to largestGroup,
            "largest_group_size" to largestGroupSize
        )
    }

    /**
     * Gets all unique regions across all groups
     */
    val allGroupedRegions: Set<String>
        get() = allGroups.flatMap { getGroupRegions(it) }.toSet()

    /**
     * Checks for orphaned groups (groups with no regions)
     */
    val orphanedGroups: List<String>
        get() = allGroups.filter { getGroupSize(it) == 0 }
}
