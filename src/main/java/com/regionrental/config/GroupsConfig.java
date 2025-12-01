package com.regionrental.config;

import com.regionrental.RegionRental;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages region groups configuration in groups.yml
 * Groups allow applying override settings to multiple regions at once
 */
public class GroupsConfig {

    private final RegionRental plugin;
    private File configFile;
    private FileConfiguration config;

    // Reserved group names that cannot be used
    private static final Set<String> RESERVED_NAMES = Set.of("all", "none", "default");

    // Group name validation pattern: alphanumeric and underscore, 2-30 characters
    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,30}$");

    public GroupsConfig(RegionRental plugin) {
        this.plugin = plugin;
        createConfig();
    }

    /**
     * Creates the groups.yml configuration file with header
     */
    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "groups.yml");

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                plugin.getLogger().info("Created groups.yml configuration file");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create groups.yml: " + e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Add header comments on first creation
        if (!config.contains("groups")) {
            config.options().header(
                "RegionRental - Region Groups Configuration\n" +
                "Use /rrgroup commands to manage groups\n" +
                "Groups allow applying override settings to multiple regions at once\n" +
                "\n" +
                "Format: Regions stored as \"world:region\" composite keys\n" +
                "\n" +
                "Commands:\n" +
                "  /rrgroup create <group_name> [regions]     # Create group\n" +
                "  /rrgroup edit <group_name> add [regions]   # Add regions\n" +
                "  /rrgroup edit <group_name> remove [regions]# Remove regions\n" +
                "  /rrgroup delete <group_name>               # Delete group\n" +
                "  /rrgroup list                              # List all groups\n" +
                "  /rrgroup view <group_name>                 # View group details"
            );
            config.set("groups", new HashMap<>());
            save();
        }
    }

    /**
     * Saves the configuration to disk
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save groups.yml: " + e.getMessage());
        }
    }

    /**
     * Reloads the configuration from disk
     */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    // ========== CRUD Operations ==========

    /**
     * Creates a new group with the specified regions
     *
     * @param groupName Group name
     * @param compositeKeys List of composite keys (world:region format)
     * @return true if created successfully, false if group already exists
     */
    public boolean createGroup(String groupName, List<String> compositeKeys) {
        if (groupExists(groupName)) {
            return false;
        }

        String path = "groups." + groupName + ".regions";
        config.set(path, compositeKeys);
        save();

        plugin.getLogger().info("Created group '" + groupName + "' with " + compositeKeys.size() + " regions");
        return true;
    }

    /**
     * Deletes a group
     *
     * @param groupName Group name
     * @return true if deleted successfully, false if group doesn't exist
     */
    public boolean deleteGroup(String groupName) {
        if (!groupExists(groupName)) {
            return false;
        }

        config.set("groups." + groupName, null);
        save();

        plugin.getLogger().info("Deleted group '" + groupName + "'");
        return true;
    }

    /**
     * Adds regions to an existing group
     *
     * @param groupName Group name
     * @param compositeKeys List of composite keys to add
     * @return true if added successfully, false if group doesn't exist
     */
    public boolean addRegionsToGroup(String groupName, List<String> compositeKeys) {
        if (!groupExists(groupName)) {
            return false;
        }

        List<String> currentRegions = getGroupRegions(groupName);

        // Add new regions (avoiding duplicates)
        for (String key : compositeKeys) {
            if (!currentRegions.contains(key)) {
                currentRegions.add(key);
            }
        }

        String path = "groups." + groupName + ".regions";
        config.set(path, currentRegions);
        save();

        plugin.getLogger().info("Added " + compositeKeys.size() + " regions to group '" + groupName + "'");
        return true;
    }

    /**
     * Removes regions from a group
     *
     * @param groupName Group name
     * @param compositeKeys List of composite keys to remove
     * @return true if removed successfully, false if group doesn't exist
     */
    public boolean removeRegionsFromGroup(String groupName, List<String> compositeKeys) {
        if (!groupExists(groupName)) {
            return false;
        }

        List<String> currentRegions = getGroupRegions(groupName);
        currentRegions.removeAll(compositeKeys);

        String path = "groups." + groupName + ".regions";
        config.set(path, currentRegions);
        save();

        plugin.getLogger().info("Removed " + compositeKeys.size() + " regions from group '" + groupName + "'");
        return true;
    }

    // ========== Query Methods ==========

    /**
     * Gets all regions in a group
     *
     * @param groupName Group name
     * @return List of composite keys, empty list if group doesn't exist
     */
    public List<String> getGroupRegions(String groupName) {
        String path = "groups." + groupName + ".regions";
        List<String> regions = config.getStringList(path);
        return new ArrayList<>(regions);  // Return mutable copy
    }

    /**
     * Gets all group names
     *
     * @return Set of group names, empty set if no groups exist
     */
    public Set<String> getAllGroups() {
        if (!config.contains("groups")) {
            return new HashSet<>();
        }

        return config.getConfigurationSection("groups").getKeys(false);
    }

    /**
     * Checks if a group exists
     *
     * @param groupName Group name
     * @return true if group exists
     */
    public boolean groupExists(String groupName) {
        return config.contains("groups." + groupName);
    }

    /**
     * Gets the number of regions in a group
     *
     * @param groupName Group name
     * @return Number of regions, 0 if group doesn't exist
     */
    public int getGroupSize(String groupName) {
        return getGroupRegions(groupName).size();
    }

    /**
     * Gets total number of groups
     *
     * @return Number of groups
     */
    public int getGroupCount() {
        return getAllGroups().size();
    }

    // ========== Membership Tracking ==========

    /**
     * Checks if a region is in any group
     *
     * @param compositeKey Composite key (world:region format)
     * @return true if region is in a group
     */
    public boolean isRegionInGroup(String compositeKey) {
        for (String groupName : getAllGroups()) {
            if (getGroupRegions(groupName).contains(compositeKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the group a region belongs to
     *
     * @param compositeKey Composite key (world:region format)
     * @return Group name, or null if region is not in any group
     */
    public String getRegionGroup(String compositeKey) {
        for (String groupName : getAllGroups()) {
            if (getGroupRegions(groupName).contains(compositeKey)) {
                return groupName;
            }
        }
        return null;
    }

    /**
     * Checks if multiple regions are in any group
     * Returns map of composite key → group name for regions that are grouped
     *
     * @param compositeKeys List of composite keys to check
     * @return Map of composite key → group name (only includes grouped regions)
     */
    public Map<String, String> checkGroupMembership(List<String> compositeKeys) {
        Map<String, String> memberships = new HashMap<>();

        for (String key : compositeKeys) {
            String group = getRegionGroup(key);
            if (group != null) {
                memberships.put(key, group);
            }
        }

        return memberships;
    }

    // ========== Validation ==========

    /**
     * Validates a group name
     *
     * @param name Group name to validate
     * @return true if valid
     */
    public boolean isValidGroupName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // Check pattern
        if (!GROUP_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }

        // Check reserved names (case-insensitive)
        return !RESERVED_NAMES.contains(name.toLowerCase());
    }

    /**
     * Gets validation error message for a group name
     *
     * @param name Group name
     * @return Error message, or null if valid
     */
    public String getValidationError(String name) {
        if (name == null || name.isEmpty()) {
            return "Group name cannot be empty";
        }

        if (name.length() < 2) {
            return "Group name must be at least 2 characters";
        }

        if (name.length() > 30) {
            return "Group name must be 30 characters or less";
        }

        if (!GROUP_NAME_PATTERN.matcher(name).matches()) {
            return "Group name can only contain letters, numbers, and underscores";
        }

        if (RESERVED_NAMES.contains(name.toLowerCase())) {
            return "Group name '" + name + "' is reserved and cannot be used";
        }

        return null;  // Valid
    }

    // ========== Utility Methods ==========

    /**
     * Gets statistics about all groups
     *
     * @return Map with statistics (group_count, total_regions, etc.)
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        Set<String> allGroups = getAllGroups();
        int totalRegions = 0;
        int largestGroupSize = 0;
        String largestGroup = null;

        for (String groupName : allGroups) {
            int size = getGroupSize(groupName);
            totalRegions += size;

            if (size > largestGroupSize) {
                largestGroupSize = size;
                largestGroup = groupName;
            }
        }

        stats.put("group_count", allGroups.size());
        stats.put("total_regions", totalRegions);
        stats.put("largest_group", largestGroup);
        stats.put("largest_group_size", largestGroupSize);

        return stats;
    }

    /**
     * Gets all unique regions across all groups
     *
     * @return Set of composite keys
     */
    public Set<String> getAllGroupedRegions() {
        Set<String> allRegions = new HashSet<>();

        for (String groupName : getAllGroups()) {
            allRegions.addAll(getGroupRegions(groupName));
        }

        return allRegions;
    }

    /**
     * Checks for orphaned groups (groups with no regions)
     *
     * @return List of group names with no regions
     */
    public List<String> getOrphanedGroups() {
        return getAllGroups().stream()
                .filter(group -> getGroupSize(group) == 0)
                .collect(Collectors.toList());
    }
}
