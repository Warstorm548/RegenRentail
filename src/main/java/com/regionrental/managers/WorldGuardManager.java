package com.regionrental.managers;

import com.regionrental.RegionRental;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WorldGuardManager {
    
    private final RegionRental plugin;
    private WorldGuardPlugin worldGuard;
    
    public WorldGuardManager(RegionRental plugin) {
        this.plugin = plugin;
        setupWorldGuard();
    }
    
    private void setupWorldGuard() {
        org.bukkit.plugin.Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        
        if (wgPlugin == null || !(wgPlugin instanceof WorldGuardPlugin)) {
            plugin.getLogger().severe("WorldGuard not found!");
            return;
        }
        
        this.worldGuard = (WorldGuardPlugin) wgPlugin;
        plugin.getLogger().info("WorldGuard integration enabled");
    }
    
    public boolean regionExists(String regionName, World world) {
        if (worldGuard == null) return false;
        
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        
        if (regionManager == null) return false;
        
        return regionManager.hasRegion(regionName);
    }
    
    public boolean regionExists(String regionName) {
        // Check all worlds for the region
        for (World world : Bukkit.getWorlds()) {
            if (regionExists(regionName, world)) {
                return true;
            }
        }
        return false;
    }
    
    public void addPlayerToRegion(String regionName, World world, UUID playerUUID) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            plugin.getLogger().warning("No RegionManager found for world: " + world.getName());
            return;
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region != null) {
            DefaultDomain members = region.getMembers();
            members.addPlayer(playerUUID);

            plugin.getLogger().info("Added player " + playerUUID + " to region " + regionName + " in world " + world.getName());
        } else {
            plugin.getLogger().warning("Region " + regionName + " not found in world " + world.getName());
        }
    }

    // Backward compatibility - searches all worlds
    @Deprecated
    public void addPlayerToRegion(String regionName, UUID playerUUID) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));

            if (regionManager == null) continue;

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region != null) {
                DefaultDomain members = region.getMembers();
                members.addPlayer(playerUUID);

                plugin.getLogger().info("Added player " + playerUUID + " to region " + regionName);
                return;
            }
        }
    }
    
    public void removePlayerFromRegion(String regionName, World world, UUID playerUUID) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            plugin.getLogger().warning("No RegionManager found for world: " + world.getName());
            return;
        }

        ProtectedRegion region = regionManager.getRegion(regionName);
        if (region != null) {
            DefaultDomain members = region.getMembers();
            members.removePlayer(playerUUID);

            plugin.getLogger().info("Removed player " + playerUUID + " from region " + regionName + " in world " + world.getName());
        } else {
            plugin.getLogger().warning("Region " + regionName + " not found in world " + world.getName());
        }
    }

    // Backward compatibility - searches all worlds
    @Deprecated
    public void removePlayerFromRegion(String regionName, UUID playerUUID) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));

            if (regionManager == null) continue;

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region != null) {
                DefaultDomain members = region.getMembers();
                members.removePlayer(playerUUID);

                plugin.getLogger().info("Removed player " + playerUUID + " from region " + regionName);
                return;
            }
        }
    }
    
    public boolean isPlayerMemberOfRegion(String regionName, UUID playerUUID) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            
            if (regionManager == null) continue;
            
            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region != null) {
                return region.getMembers().contains(playerUUID);
            }
        }
        return false;
    }
    
    public boolean isPlayerOwnerOfRegion(String regionName, UUID playerUUID) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            
            if (regionManager == null) continue;
            
            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region != null) {
                return region.getOwners().contains(playerUUID);
            }
        }
        return false;
    }
    
    public void clearRegionMembers(String regionName) {
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            
            if (regionManager == null) continue;
            
            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region != null) {
                region.getMembers().clear();
                plugin.getLogger().info("Cleared all members from region " + regionName);
                return;
            }
        }
    }
    
    public ProtectedRegion getRegion(String regionName, World world) {
        if (worldGuard == null) return null;
        
        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        
        if (regionManager == null) return null;
        
        return regionManager.getRegion(regionName);
    }
    
    public ProtectedRegion getRegion(String regionName) {
        for (World world : Bukkit.getWorlds()) {
            ProtectedRegion region = getRegion(regionName, world);
            if (region != null) {
                return region;
            }
        }
        return null;
    }
    
    public boolean canPlayerBuildInRegion(Player player, String regionName) {
        for (World world : Bukkit.getWorlds()) {
            ProtectedRegion region = getRegion(regionName, world);
            if (region != null) {
                LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
                return region.isMember(localPlayer) || region.isOwner(localPlayer);
            }
        }
        return false;
    }

    /**
     * Alias for regionExists() to match command naming
     */
    public boolean doesRegionExist(String regionName) {
        return regionExists(regionName);
    }

    /**
     * Get all region names across all worlds for tab completion
     */
    public java.util.Set<String> getAllRegionNames() {
        java.util.Set<String> regionNames = new java.util.HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));

            if (regionManager == null) continue;

            regionNames.addAll(regionManager.getRegions().keySet());
        }

        return regionNames;
    }
}
