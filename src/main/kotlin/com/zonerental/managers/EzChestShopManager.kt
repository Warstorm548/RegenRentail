package com.zonerental.managers

import com.zonerental.ZoneRental
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Level

/**
 * Manager for EzChestShop integration.
 * Uses reflection to access EzChestShop API and removes shops before region regeneration.
 */
class EzChestShopManager(private val plugin: ZoneRental) {

    private var ezChestShopEnabled = false

    // Reflection cache for shop detection and removal
    private var shopContainerClass: Class<*>? = null
    private var getShopMethod: Method? = null
    private var isShopMethod: Method? = null
    private var deleteShopMethod: Method? = null

    companion object {
        // Container types that can be EzChestShop shops
        private val SHOP_CONTAINER_TYPES = setOf(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL
        )

        private val POSSIBLE_PLUGIN_NAMES = arrayOf(
            "EzChestShop", "EzChestShopReborn", "ecs", "ChestShop", "ezchestshop"
        )
    }

    init {
        initializeReflection()
    }

    private fun initializeReflection() {
        // Try multiple possible plugin names (including Reborn variant)
        val ezChestShop = POSSIBLE_PLUGIN_NAMES
            .firstNotNullOfOrNull { Bukkit.getPluginManager().getPlugin(it) }

        if (ezChestShop == null) {
            plugin.logger.info("EzChestShop not detected - shop removal integration disabled")
            plugin.logger.info("Tried names: ${POSSIBLE_PLUGIN_NAMES.joinToString(", ")}")
            return
        }

        plugin.logger.info("Found EzChestShop plugin as: ${ezChestShop.name}")

        // Check if plugin is enabled - if not, schedule delayed initialization
        if (!ezChestShop.isEnabled) {
            plugin.logger.info("EzChestShop found but not yet enabled - will retry in 1 second...")
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (ezChestShop.isEnabled) {
                    plugin.logger.info("EzChestShop now enabled - initializing integration...")
                    continueInitialization(ezChestShop)
                } else {
                    plugin.logger.warning("EzChestShop still not enabled after delay - integration disabled")
                }
            }, 20L) // 1 second delay
            return
        }

        // Plugin is already enabled - continue immediately
        continueInitialization(ezChestShop)
    }

    private fun continueInitialization(ezChestShop: Plugin) {
        plugin.logger.info("EzChestShop detected (v${ezChestShop.description.version}) - initializing integration...")

        try {
            shopContainerClass = Class.forName("me.deadlight.ezchestshop.data.ShopContainer")

            // Try different method names for compatibility
            getShopMethod = runCatching {
                shopContainerClass?.getMethod("getShop", Location::class.java)
            }.onFailure {
                plugin.logger.fine("getShop(Location) not found")
            }.getOrNull()

            isShopMethod = runCatching {
                shopContainerClass?.getMethod("isShop", Location::class.java)
            }.onFailure {
                plugin.logger.fine("isShop(Location) not found")
            }.getOrNull()

            // Try to find deleteShop method for shop removal
            deleteShopMethod = runCatching {
                shopContainerClass?.getMethod("deleteShop", Location::class.java)
            }.getOrElse {
                runCatching {
                    shopContainerClass?.getMethod("removeShop", Location::class.java)
                }.onFailure {
                    plugin.logger.warning("No deleteShop/removeShop method found - shop removal will not work")
                }.getOrNull()
            }

            if (getShopMethod != null || isShopMethod != null) {
                ezChestShopEnabled = true
                plugin.logger.info("EzChestShop integration enabled successfully")
                if (deleteShopMethod != null) {
                    plugin.logger.info("Shop removal API method available")
                }
            } else {
                plugin.logger.warning("EzChestShop integration failed - API methods not found")
            }

        } catch (e: ClassNotFoundException) {
            plugin.logger.warning("EzChestShop ShopContainer class not found: ${e.message}")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to initialize EzChestShop integration", e)
        }
    }

    val isEnabled: Boolean
        get() = ezChestShopEnabled

    fun hasShopAt(location: Location): Boolean {
        if (!ezChestShopEnabled) return false

        return runCatching {
            isShopMethod?.let { method ->
                val result = method.invoke(null, location)
                if (result is Boolean) return result
            }
            getShopMethod?.let { method ->
                val shop = method.invoke(null, location)
                return shop != null
            }
            false
        }.onFailure { e ->
            if (plugin.configManager.isDebugMode) {
                plugin.logger.fine("Error checking shop at ${formatLocation(location)}: ${e.message}")
            }
        }.getOrDefault(false)
    }

    fun removeShopAt(location: Location): Boolean {
        if (!ezChestShopEnabled) return false

        val debug = plugin.configManager.isDebugMode

        if (!hasShopAt(location)) {
            if (debug) {
                plugin.logger.info("[Debug] No shop found at ${formatLocation(location)}")
            }
            return false
        }

        if (debug) {
            plugin.logger.info("[Debug] Removing shop at ${formatLocation(location)}")
        }

        // Call ShopContainer.deleteShop(location) via reflection
        // This handles: database removal, hologram cleanup, persistent data, memory cleanup
        val deleteMethod = deleteShopMethod
        if (deleteMethod != null) {
            return runCatching {
                deleteMethod.invoke(null, location)

                if (debug) {
                    plugin.logger.info("[Debug] Called deleteShop API at ${formatLocation(location)}")
                }

                // Verify shop was removed
                val removed = !hasShopAt(location)

                if (removed && debug) {
                    plugin.logger.info("[Debug] Shop successfully removed at ${formatLocation(location)}")
                } else if (!removed) {
                    plugin.logger.warning("Shop removal API call failed at ${formatLocation(location)}")
                }

                removed
            }.onFailure { e ->
                plugin.logger.warning("Failed to call deleteShop API: ${e.message}")
            }.getOrDefault(false)
        } else {
            plugin.logger.warning("deleteShop method not available - cannot remove shop at ${formatLocation(location)}")
            return false
        }
    }

    /**
     * Remove all chest shops from a WorldGuard region (world-aware).
     *
     * @param regionName The name of the WorldGuard region
     * @param world The world containing the region
     * @return Number of shops removed
     */
    fun removeShopsInRegion(regionName: String, world: World): Int {
        if (!ezChestShopEnabled) return 0

        if (!plugin.configManager.isEzChestShopRemovalEnabled) {
            return 0
        }

        // Get the WorldGuard region from the specified world
        val region = plugin.worldGuardManager.getRegion(regionName, world)
        if (region == null) {
            plugin.logger.warning("Cannot remove shops from region '$regionName' in world '${world.name}' - region not found")
            return 0
        }

        return removeShopsInRegionInternal(region, world, regionName)
    }

    /**
     * Remove all chest shops from a WorldGuard region (searches all worlds).
     * This method maintains the original signature for backward compatibility.
     *
     * @param regionName The name of the WorldGuard region
     * @return Number of shops removed
     */
    @Deprecated("Use removeShopsInRegion(String, World) instead")
    fun removeShopsInRegion(regionName: String): Int {
        if (!ezChestShopEnabled) return 0

        if (!plugin.configManager.isEzChestShopRemovalEnabled) {
            return 0
        }

        // Find the world containing this region
        val world = findWorldForRegion(regionName)
        if (world == null) {
            plugin.logger.warning("Cannot remove shops from region '$regionName' - world not found")
            return 0
        }

        // Get the WorldGuard region
        val region = plugin.worldGuardManager.getRegion(regionName, world)
        if (region == null) {
            plugin.logger.warning("Cannot remove shops from region '$regionName' - region not found")
            return 0
        }

        return removeShopsInRegionInternal(region, world, regionName)
    }

    /**
     * Internal method to remove shops from a region.
     */
    private fun removeShopsInRegionInternal(
        region: com.sk89q.worldguard.protection.regions.ProtectedRegion,
        world: World,
        regionId: String
    ): Int {
        val debug = plugin.configManager.isDebugMode
        val min = region.minimumPoint
        val max = region.maximumPoint

        if (debug) {
            plugin.logger.info("[Debug] Scanning region '$regionId' for shops")
        }

        // Find all shops
        val shopLocations = mutableListOf<Location>()
        for (x in min.x()..max.x()) {
            for (y in min.y()..max.y()) {
                for (z in min.z()..max.z()) {
                    val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    val block = loc.block
                    if (block.type in SHOP_CONTAINER_TYPES && hasShopAt(loc)) {
                        shopLocations.add(loc)
                    }
                }
            }
        }

        if (shopLocations.isEmpty()) {
            if (debug) {
                plugin.logger.info("[Debug] No shops found in region '$regionId'")
            }
            return 0
        }

        plugin.logger.info("Found ${shopLocations.size} EzChestShop(s) in region '$regionId'")

        // Remove all shops
        var removedCount = 0
        for (shopLoc in shopLocations) {
            if (removeShopAt(shopLoc)) {
                removedCount++
            }
        }

        plugin.logger.info("Removed $removedCount/${shopLocations.size} shop(s) from '$regionId'")
        return removedCount
    }

    /**
     * Find the world that contains the specified region.
     */
    private fun findWorldForRegion(regionName: String): World? {
        return Bukkit.getWorlds().firstOrNull { world ->
            plugin.worldGuardManager.regionExists(regionName, world)
        }
    }

    /**
     * Notify a player that shops were removed from their rental.
     */
    fun notifyPlayer(playerUUID: UUID, regionId: String, shopCount: Int) {
        if (!plugin.configManager.isEzChestShopNotifyEnabled) return

        Bukkit.getPlayer(playerUUID)?.let { player ->
            if (player.isOnline) {
                val message = plugin.configManager.ezChestShopRemovalMessage
                    .replace("{region}", regionId)
                    .replace("{count}", shopCount.toString())
                    .replace("&", "§")
                player.sendMessage(message)
            }
        }
    }

    private fun formatLocation(loc: Location): String {
        return "${loc.world.name} @ ${loc.blockX},${loc.blockY},${loc.blockZ}"
    }

    fun reload() {
        ezChestShopEnabled = false
        shopContainerClass = null
        getShopMethod = null
        isShopMethod = null
        deleteShopMethod = null
        initializeReflection()
    }
}
