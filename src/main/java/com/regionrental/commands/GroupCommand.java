package com.regionrental.commands;

import com.regionrental.RegionRental;
import com.regionrental.util.WorldRegionParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles /rrgroup commands for region group management
 * Supports hybrid input: arguments or chat prompts
 */
public class GroupCommand implements CommandExecutor, TabCompleter {

    private final RegionRental plugin;

    // Pending actions for chat-based input (Phase 2)
    private final Map<UUID, PendingGroupAction> pendingActions = new ConcurrentHashMap<>();

    // Timeout for pending actions (60 seconds)
    private static final long PENDING_TIMEOUT = 60 * 1000;

    public GroupCommand(RegionRental plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("regionrental.admin.group")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "create":
                return handleCreate(sender, args);
            case "edit":
                return handleEdit(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "list":
                return handleList(sender);
            case "view":
                return handleView(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subcommand);
                sendHelp(sender);
                return true;
        }
    }

    // ========== Subcommand Handlers ==========

    /**
     * Handles /rrgroup create <group_name> [regions]
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrgroup create <group_name> [regions]");
            return true;
        }

        String groupName = args[1];

        // Validate group name
        String validationError = plugin.getGroupsConfig().getValidationError(groupName);
        if (validationError != null) {
            sender.sendMessage(ChatColor.RED + validationError);
            return true;
        }

        // Check if group already exists
        if (plugin.getGroupsConfig().groupExists(groupName)) {
            sender.sendMessage(ChatColor.RED + "Group '" + groupName + "' already exists!");
            return true;
        }

        // Hybrid approach: regions provided or prompt
        if (args.length > 2) {
            // Regions provided as arguments
            String regionsInput = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            processCreateGroup(sender, groupName, regionsInput);
        } else {
            // Prompt for regions (Phase 2)
            promptForRegions(sender, groupName, "create");
        }

        return true;
    }

    /**
     * Handles /rrgroup edit <group_name> <add|remove> [regions]
     */
    private boolean handleEdit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrgroup edit <group_name> <add|remove> [regions]");
            return true;
        }

        String groupName = args[1];
        String action = args[2].toLowerCase();

        // Validate group exists
        if (!plugin.getGroupsConfig().groupExists(groupName)) {
            sender.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist!");
            return true;
        }

        // Validate action
        if (!action.equals("add") && !action.equals("remove")) {
            sender.sendMessage(ChatColor.RED + "Action must be 'add' or 'remove'");
            return true;
        }

        // Hybrid approach
        if (args.length > 3) {
            // Regions provided as arguments
            String regionsInput = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            if (action.equals("add")) {
                processAddRegions(sender, groupName, regionsInput);
            } else {
                processRemoveRegions(sender, groupName, regionsInput);
            }
        } else {
            // Prompt for regions (Phase 2)
            promptForRegions(sender, groupName, action);
        }

        return true;
    }

    /**
     * Handles /rrgroup delete <group_name> [confirm]
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrgroup delete <group_name> [confirm]");
            return true;
        }

        String groupName = args[1];

        // Validate group exists
        if (!plugin.getGroupsConfig().groupExists(groupName)) {
            sender.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist!");
            return true;
        }

        // Require confirmation
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            int regionCount = plugin.getGroupsConfig().getGroupSize(groupName);
            sender.sendMessage(ChatColor.YELLOW + "WARNING: You are about to delete group '" + groupName + "' with " + regionCount + " regions!");
            sender.sendMessage(ChatColor.YELLOW + "Group overrides in regions.yml will be removed.");
            sender.sendMessage(ChatColor.YELLOW + "Individual region overrides will remain intact.");
            sender.sendMessage(ChatColor.RED + "Type: /rrgroup delete " + groupName + " confirm");
            return true;
        }

        // Delete group from groups.yml
        plugin.getGroupsConfig().deleteGroup(groupName);

        // Remove group overrides from regions.yml
        plugin.getRegionsConfig().removeGroupOverrides(groupName);

        sender.sendMessage(ChatColor.GREEN + "Deleted group '" + groupName + "' and removed all group overrides");

        return true;
    }

    /**
     * Handles /rrgroup list
     */
    private boolean handleList(CommandSender sender) {
        Set<String> allGroups = plugin.getGroupsConfig().getAllGroups();

        if (allGroups.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No groups exist. Create one with /rrgroup create <name>");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Region Groups ===");

        for (String groupName : allGroups) {
            int size = plugin.getGroupsConfig().getGroupSize(groupName);
            sender.sendMessage(ChatColor.GREEN + "  " + groupName + ChatColor.GRAY + " (" + size + " regions)");
        }

        Map<String, Object> stats = plugin.getGroupsConfig().getStatistics();
        int totalGroups = (int) stats.get("group_count");
        int totalRegions = (int) stats.get("total_regions");

        sender.sendMessage(ChatColor.GOLD + "Total: " + totalGroups + " groups, " + totalRegions + " regions");
        sender.sendMessage(ChatColor.GRAY + "Use /rrgroup view <group_name> for details");

        return true;
    }

    /**
     * Handles /rrgroup view <group_name>
     */
    private boolean handleView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rrgroup view <group_name>");
            return true;
        }

        String groupName = args[1];

        // Validate group exists
        if (!plugin.getGroupsConfig().groupExists(groupName)) {
            sender.sendMessage(ChatColor.RED + "Group '" + groupName + "' does not exist!");
            return true;
        }

        List<String> regions = plugin.getGroupsConfig().getGroupRegions(groupName);

        sender.sendMessage(ChatColor.GOLD + "=== Group: " + groupName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Regions (" + regions.size() + "):");

        for (String compositeKey : regions) {
            sender.sendMessage(ChatColor.GREEN + "  - " + compositeKey);
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Apply overrides: /rroverride <setting> " + groupName + " <value>");

        return true;
    }

    // ========== Processing Methods ==========

    /**
     * Processes group creation with region input
     */
    private void processCreateGroup(CommandSender sender, String groupName, String regionsInput) {
        // Parse regions
        ParseResult parseResult = parseRegionInput(sender, regionsInput);

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No valid regions provided!");
            if (!parseResult.errors.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Errors:");
                parseResult.errors.forEach(error -> sender.sendMessage(ChatColor.YELLOW + "  - " + error));
            }
            return;
        }

        // Check for duplicate membership
        Map<String, String> duplicates = plugin.getGroupsConfig().checkGroupMembership(parseResult.validRegions);

        if (!duplicates.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Some regions are already in groups:");
            for (Map.Entry<String, String> entry : duplicates.entrySet()) {
                sender.sendMessage(ChatColor.YELLOW + "  - " + entry.getKey() + " is in group '" + entry.getValue() + "'");
            }
            sender.sendMessage(ChatColor.RED + "Remove them from their groups first.");
            return;
        }

        // Create group
        plugin.getGroupsConfig().createGroup(groupName, parseResult.validRegions);

        sender.sendMessage(ChatColor.GREEN + "Created group '" + groupName + "' with " + parseResult.validRegions.size() + " regions");

        if (!parseResult.errors.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Warnings (" + parseResult.errors.size() + " regions skipped):");
            parseResult.errors.forEach(error -> sender.sendMessage(ChatColor.YELLOW + "  - " + error));
        }
    }

    /**
     * Processes adding regions to a group
     */
    private void processAddRegions(CommandSender sender, String groupName, String regionsInput) {
        // Parse regions
        ParseResult parseResult = parseRegionInput(sender, regionsInput);

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No valid regions provided!");
            return;
        }

        // Check for duplicate membership (including this group)
        Map<String, String> duplicates = plugin.getGroupsConfig().checkGroupMembership(parseResult.validRegions);

        if (!duplicates.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Some regions are already in groups:");
            for (Map.Entry<String, String> entry : duplicates.entrySet()) {
                String existingGroup = entry.getValue();
                if (existingGroup.equals(groupName)) {
                    sender.sendMessage(ChatColor.YELLOW + "  - " + entry.getKey() + " is already in this group");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "  - " + entry.getKey() + " is in group '" + existingGroup + "'");
                }
            }
            sender.sendMessage(ChatColor.RED + "Remove them from other groups first.");
            return;
        }

        // Add regions
        plugin.getGroupsConfig().addRegionsToGroup(groupName, parseResult.validRegions);

        int totalSize = plugin.getGroupsConfig().getGroupSize(groupName);
        sender.sendMessage(ChatColor.GREEN + "Added " + parseResult.validRegions.size() + " regions to group '" + groupName + "' (Total: " + totalSize + " regions)");

        if (!parseResult.errors.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Warnings (" + parseResult.errors.size() + " regions skipped):");
            parseResult.errors.forEach(error -> sender.sendMessage(ChatColor.YELLOW + "  - " + error));
        }
    }

    /**
     * Processes removing regions from a group
     */
    private void processRemoveRegions(CommandSender sender, String groupName, String regionsInput) {
        // Parse regions
        ParseResult parseResult = parseRegionInput(sender, regionsInput);

        if (parseResult.validRegions.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No valid regions provided!");
            return;
        }

        // Get current regions in group
        List<String> currentRegions = plugin.getGroupsConfig().getGroupRegions(groupName);

        // Check which regions are actually in the group
        List<String> toRemove = new ArrayList<>();
        List<String> notInGroup = new ArrayList<>();

        for (String region : parseResult.validRegions) {
            if (currentRegions.contains(region)) {
                toRemove.add(region);
            } else {
                notInGroup.add(region);
            }
        }

        if (toRemove.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "None of the specified regions are in group '" + groupName + "'");
            return;
        }

        // Remove regions
        plugin.getGroupsConfig().removeRegionsFromGroup(groupName, toRemove);

        int remainingSize = plugin.getGroupsConfig().getGroupSize(groupName);
        sender.sendMessage(ChatColor.GREEN + "Removed " + toRemove.size() + " regions from group '" + groupName + "' (Remaining: " + remainingSize + " regions)");

        if (!notInGroup.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Note: " + notInGroup.size() + " regions were not in the group");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Parses comma-separated region input into composite keys
     */
    private ParseResult parseRegionInput(CommandSender sender, String input) {
        List<String> validRegions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Split by comma and trim
        String[] parts = input.split(",");
        World defaultWorld = sender instanceof Player ? ((Player) sender).getWorld() : null;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // Parse region with world awareness
            String compositeKey;

            if (trimmed.contains(":")) {
                // Explicit world:region format
                String[] worldRegion = trimmed.split(":", 2);
                String worldName = worldRegion[0];
                String regionName = worldRegion[1];

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    errors.add("World '" + worldName + "' not found");
                    continue;
                }

                compositeKey = worldName + ":" + regionName;

                // Validate region exists
                if (!plugin.getWorldGuardManager().regionExists(regionName, world)) {
                    errors.add("Region '" + compositeKey + "' not found in WorldGuard");
                    continue;
                }
            } else {
                // No world prefix - use default world
                if (defaultWorld == null) {
                    errors.add("Console must use world:region format (e.g., world:" + trimmed + ")");
                    continue;
                }

                compositeKey = defaultWorld.getName() + ":" + trimmed;

                // Validate region exists
                if (!plugin.getWorldGuardManager().regionExists(trimmed, defaultWorld)) {
                    errors.add("Region '" + compositeKey + "' not found in WorldGuard");
                    continue;
                }
            }

            validRegions.add(compositeKey);
        }

        return new ParseResult(validRegions, errors);
    }

    /**
     * Prompts for region input (Phase 2 implementation)
     */
    private void promptForRegions(CommandSender sender, String groupName, String action) {
        // Phase 2: Will implement chat listener integration
        // For now, just tell user to use arguments
        sender.sendMessage(ChatColor.YELLOW + "Please provide regions as arguments:");

        switch (action) {
            case "create":
                sender.sendMessage(ChatColor.GRAY + "Usage: /rrgroup create " + groupName + " region1,region2,world:region3");
                break;
            case "add":
                sender.sendMessage(ChatColor.GRAY + "Usage: /rrgroup edit " + groupName + " add region1,region2");
                break;
            case "remove":
                sender.sendMessage(ChatColor.GRAY + "Usage: /rrgroup edit " + groupName + " remove region1,region2");
                break;
        }

        sender.sendMessage(ChatColor.GRAY + "Use world:region format for cross-world regions");
    }

    // ========== Pending Actions (Phase 2) ==========

    /**
     * Checks if player has a pending action
     */
    public boolean hasPendingAction(UUID playerUUID) {
        return pendingActions.containsKey(playerUUID);
    }

    /**
     * Handles chat input for pending actions
     */
    public void handleChatInput(Player player, String message) {
        PendingGroupAction action = pendingActions.remove(player.getUniqueId());

        if (action == null) {
            return;
        }

        // Process based on action type
        switch (action.action) {
            case "create":
                processCreateGroup(player, action.groupName, message);
                break;
            case "add":
                processAddRegions(player, action.groupName, message);
                break;
            case "remove":
                processRemoveRegions(player, action.groupName, message);
                break;
        }
    }

    /**
     * Cleans up expired pending actions
     */
    public void cleanupExpiredActions() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, PendingGroupAction> entry : pendingActions.entrySet()) {
            if (now - entry.getValue().timestamp > PENDING_TIMEOUT) {
                expired.add(entry.getKey());
            }
        }

        for (UUID uuid : expired) {
            pendingActions.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "Region input timed out. Please run the command again.");
            }
        }
    }

    // ========== Tab Completion ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("regionrental.admin.group")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            completions.addAll(Arrays.asList("create", "edit", "delete", "list", "view"));
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("edit") || subcommand.equals("delete") || subcommand.equals("view")) {
                // Group names
                completions.addAll(plugin.getGroupsConfig().getAllGroups());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("edit")) {
                // Add or remove
                completions.addAll(Arrays.asList("add", "remove"));
            } else if (args[0].equalsIgnoreCase("delete")) {
                // Confirm
                completions.add("confirm");
            }
        }

        // Filter by current input
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }

    // ========== Help ==========

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionRental Groups ===");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup create <name> [regions]" + ChatColor.GRAY + " - Create group");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup edit <name> add [regions]" + ChatColor.GRAY + " - Add regions");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup edit <name> remove [regions]" + ChatColor.GRAY + " - Remove regions");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup delete <name>" + ChatColor.GRAY + " - Delete group");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup list" + ChatColor.GRAY + " - List all groups");
        sender.sendMessage(ChatColor.YELLOW + "/rrgroup view <name>" + ChatColor.GRAY + " - View group details");
        sender.sendMessage(ChatColor.GRAY + "Regions format: region1,region2,world:region3");
    }

    // ========== Inner Classes ==========

    /**
     * Pending action for chat-based input
     */
    static class PendingGroupAction {
        String groupName;
        String action;  // "create", "add", "remove"
        long timestamp;

        PendingGroupAction(String groupName, String action) {
            this.groupName = groupName;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Result of parsing region input
     */
    static class ParseResult {
        List<String> validRegions;
        List<String> errors;

        ParseResult(List<String> validRegions, List<String> errors) {
            this.validRegions = validRegions;
            this.errors = errors;
        }
    }
}
