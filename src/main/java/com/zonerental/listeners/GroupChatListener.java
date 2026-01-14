package com.zonerental.listeners;

import com.zonerental.ZoneRental;
import com.zonerental.commands.GroupCommand;
import com.zonerental.commands.PendingGroupAction;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Listens for chat input when players have pending group actions
 * Part of the hybrid command system (arguments or chat prompts)
 */
public class GroupChatListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ZoneRental plugin;
    private final GroupCommand groupCommand;

    public GroupChatListener(ZoneRental plugin) {
        this.plugin = plugin;
        this.groupCommand = plugin.getGroupCommand();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Check if player has a pending group action
        PendingGroupAction pendingAction = groupCommand.getPendingAction(playerUUID);
        if (pendingAction == null) {
            return;  // No pending action, let chat proceed normally
        }

        // Check if action has expired (60 second timeout)
        long currentTime = System.currentTimeMillis();
        if (currentTime - pendingAction.getTimestamp() > GroupCommand.PENDING_TIMEOUT) {
            groupCommand.cancelPendingAction(playerUUID);
            player.sendMessage(MINI_MESSAGE.deserialize("<red>Group action timed out. Please try again."));
            event.setCancelled(true);
            return;
        }

        // Cancel the chat event (don't broadcast the region input)
        event.setCancelled(true);

        // Get the chat message as region input
        String regionsInput = event.getMessage().trim();

        // Allow player to cancel with "cancel" or "stop"
        if (regionsInput.equalsIgnoreCase("cancel") || regionsInput.equalsIgnoreCase("stop")) {
            groupCommand.cancelPendingAction(playerUUID);
            player.sendMessage(MINI_MESSAGE.deserialize("<yellow>Group action cancelled."));
            return;
        }

        // Validate input is not empty
        if (regionsInput.isEmpty()) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>Please provide at least one region name."));
            player.sendMessage(MINI_MESSAGE.deserialize("<gray>Format: region1,region2 or world:region1,region2"));
            player.sendMessage(MINI_MESSAGE.deserialize("<gray>Type 'cancel' to abort."));
            return;
        }

        // Process the action based on type
        String action = pendingAction.getAction();
        String groupName = pendingAction.getGroupName();

        // Remove pending action before processing (prevents duplicate processing)
        groupCommand.cancelPendingAction(playerUUID);

        // Process on main thread (required for Bukkit API operations)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (action) {
                case "create":
                    groupCommand.processCreateGroup(player, groupName, regionsInput);
                    break;
                case "add":
                    groupCommand.processAddRegions(player, groupName, regionsInput);
                    break;
                case "remove":
                    groupCommand.processRemoveRegions(player, groupName, regionsInput);
                    break;
                default:
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Unknown action type: " + action));
                    break;
            }
        });
    }
}
