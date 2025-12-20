package com.regionrental.config

import com.regionrental.RegionRental
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.logging.Level

class StorageConfig(private val plugin: RegionRental) {

    private val configFile: File = File(plugin.dataFolder, "storage.yml")
    private var config: YamlConfiguration

    @Volatile
    var isDirty: Boolean = false
        private set

    init {
        createConfig()
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    private fun createConfig() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            try {
                configFile.createNewFile()
                plugin.logger.info("Created storage.yml file")
            } catch (e: IOException) {
                plugin.logger.log(Level.SEVERE, "Could not create storage.yml!", e)
            }
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
            plugin.logger.log(Level.SEVERE, "Could not save storage.yml!", e)
            return
        }
        isDirty = false
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        isDirty = false
    }

    fun storeItems(playerUUID: UUID, region: String, items: List<ItemStack>) {
        storeItems(playerUUID, region, items, emptyList())
    }

    fun storeItems(playerUUID: UUID, region: String, containerItems: List<ItemStack>, blockItems: List<ItemStack>) {
        val path = "storage.$playerUUID.$region"
        config.set("$path.timestamp", System.currentTimeMillis())
        config.set("$path.items", containerItems)
        config.set("$path.blocks", blockItems)
        markDirty()
    }

    fun retrieveItems(playerUUID: UUID, region: String): List<ItemStack> {
        val path = "storage.$playerUUID.$region.items"

        if (!config.contains(path)) {
            return mutableListOf()
        }

        @Suppress("UNCHECKED_CAST")
        val items = config.getList(path, mutableListOf<ItemStack>()) as? List<ItemStack> ?: mutableListOf()

        // Remove the items after retrieval
        config.set("storage.$playerUUID.$region", null)

        // Clean up empty sections
        config.getConfigurationSection("storage.$playerUUID")?.let { section ->
            if (section.getKeys(false).isEmpty()) {
                config.set("storage.$playerUUID", null)
            }
        }

        markDirty()
        return items
    }

    fun getAllStoredItems(playerUUID: UUID): List<ItemStack> {
        val allItems = mutableListOf<ItemStack>()
        val basePath = "storage.$playerUUID"

        val section = config.getConfigurationSection(basePath) ?: return allItems

        for (region in section.getKeys(false)) {
            @Suppress("UNCHECKED_CAST")
            val items = config.getList("$basePath.$region.items", mutableListOf<ItemStack>()) as? List<ItemStack>
            items?.let { allItems.addAll(it) }

            @Suppress("UNCHECKED_CAST")
            val blocks = config.getList("$basePath.$region.blocks", mutableListOf<ItemStack>()) as? List<ItemStack>
            blocks?.let { allItems.addAll(it) }
        }

        return allItems
    }

    fun clearPlayerStorage(playerUUID: UUID) {
        config.set("storage.$playerUUID", null)
        markDirty()
    }

    /**
     * Updates storage with remaining items after partial retrieval
     * If no items remain, clears storage completely
     */
    fun updatePartialStorage(playerUUID: UUID, remainingItems: List<ItemStack>) {
        if (remainingItems.isEmpty()) {
            clearPlayerStorage(playerUUID)
            return
        }

        // Clear existing storage first
        clearPlayerStorage(playerUUID)

        // Re-store remaining items under a generic region name
        storeItems(playerUUID, "partial_retrieval", remainingItems, emptyList())
    }

    fun hasStoredItems(playerUUID: UUID): Boolean =
        config.contains("storage.$playerUUID")

    fun getStoredRegions(playerUUID: UUID): List<String> {
        val basePath = "storage.$playerUUID"
        val section = config.getConfigurationSection(basePath) ?: return emptyList()
        return section.getKeys(false).toList()
    }

    fun getStorageTimestamp(playerUUID: UUID, region: String): Long =
        config.getLong("storage.$playerUUID.$region.timestamp", 0L)

    fun cleanupOldStorage(maxAge: Long) {
        val currentTime = System.currentTimeMillis()

        val storageSection = config.getConfigurationSection("storage") ?: return

        for (uuid in storageSection.getKeys(false)) {
            val playerSection = config.getConfigurationSection("storage.$uuid") ?: continue

            for (region in playerSection.getKeys(false)) {
                val timestamp = config.getLong("storage.$uuid.$region.timestamp", 0L)

                if (currentTime - timestamp > maxAge) {
                    config.set("storage.$uuid.$region", null)
                    plugin.logger.info("Cleaned up old storage for $uuid in region $region")
                }
            }
        }

        markDirty()
    }
}
