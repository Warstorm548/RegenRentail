package com.regionrental;

import com.regionrental.commands.*;
import com.regionrental.config.ConfigManager;
import com.regionrental.config.RegionsConfig;
import com.regionrental.config.SignsConfig;
import com.regionrental.config.StorageConfig;
import com.regionrental.listeners.SignInteractListener;
import com.regionrental.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class RegionRental extends JavaPlugin {
    
    private static RegionRental instance;
    
    // Core managers
    private ConfigManager configManager;
    private SignsConfig signsConfig;
    private StorageConfig storageConfig;
    private RegionsConfig regionsConfig;
    private RentalManager rentalManager;
    private SignManager signManager;
    private StorageManager storageManager;
    private ExpirationManager expirationManager;
    private WorldGuardManager worldGuardManager;
    private WorldEditManager worldEditManager;
    private EzChestShopManager ezChestShopManager;

    // Economy
    private Economy economy;

    // Track command prefix
    private String activePrefix;
    private boolean usingFallback = false;
    private List<String> registeredCommands = new ArrayList<>();

    // Track if aliases are enabled
    private boolean aliasesEnabled = false;
    private List<String> registeredAliases = new ArrayList<>();
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize configurations
        if (!initializeConfigs()) {
            getLogger().severe("Failed to initialize configurations! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Check dependencies
        if (!checkDependencies()) {
            getLogger().severe("Missing required dependencies! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Setup economy
        if (!setupEconomy()) {
            getLogger().severe("Failed to setup economy! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize managers
        initializeManagers();
        
        // Register commands
        registerCommands();
        
        // Register listeners
        registerListeners();
        
        // Start tasks
        startTasks();
        
        // Try to register command aliases if no conflicts
        registerAliasesIfPossible();
        
        getLogger().info("RegionRental has been enabled successfully!");
        getLogger().info("Commands start with /rr to avoid conflicts");
        
        if (aliasesEnabled) {
            getLogger().info("Short command aliases have been enabled!");
        } else {
            getLogger().info("Short aliases disabled due to conflicts. Use /rr prefix for all commands.");
        }
    }
    
    @Override
    public void onDisable() {
        // Save all data
        if (rentalManager != null) {
            rentalManager.saveAllRentals();
        }
        if (signsConfig != null) {
            signsConfig.save();
        }
        if (storageConfig != null) {
            storageConfig.save();
        }
        if (regionsConfig != null) {
            regionsConfig.save();
        }

        // Cancel tasks
        Bukkit.getScheduler().cancelTasks(this);

        // Unregister aliases
        unregisterAliases();

        getLogger().info("RegionRental has been disabled!");
    }
    
    private boolean initializeConfigs() {
        try {
            saveDefaultConfig();
            configManager = new ConfigManager(this);
            signsConfig = new SignsConfig(this);
            storageConfig = new StorageConfig(this);
            regionsConfig = new RegionsConfig(this);

            // Migrate regions from config.yml to regions.yml (one-time)
            regionsConfig.migrateFromMainConfig(getConfig());

            // Verify regions.yml is in sync with signs.yml (if auto-verify enabled)
            if (configManager.isAutoVerifyRegions()) {
                regionsConfig.verifyAndRepairRegions();
            }

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize configurations", e);
            return false;
        }
    }
    
    private boolean checkDependencies() {
        // Check for Vault
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault is not installed!");
            return false;
        }

        // Check for WorldGuard
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard is not installed!");
            return false;
        }

        // Check for WorldEdit
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit is not installed!");
            return false;
        }

        return true;
    }
    
    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
    
    private void initializeManagers() {
        // Initialize WorldGuard and WorldEdit managers first
        worldGuardManager = new WorldGuardManager(this);
        worldEditManager = new WorldEditManager(this);

        // Initialize integration managers
        ezChestShopManager = new EzChestShopManager(this);

        // Initialize other managers
        rentalManager = new RentalManager(this);
        signManager = new SignManager(this);
        storageManager = new StorageManager(this);
        expirationManager = new ExpirationManager(this);

        // Load data
        rentalManager.loadAllRentals();
        signManager.loadAllSigns();
    }
    
    private void registerCommands() {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            getLogger().severe("Could not access CommandMap! Commands will not be registered.");
            return;
        }

        // Get configured prefix from config
        String configuredPrefix = configManager.getCommandPrefix();

        // Determine which prefix to use based on conflicts
        activePrefix = determineActivePrefix(commandMap, configuredPrefix);

        getLogger().info("Registering commands with prefix: '" + activePrefix + "'");

        // Register all commands with determined prefix
        registerCommandWithPrefix(commandMap, activePrefix, "", new RRCommand(this), "regionrental.user");
        registerCommandWithPrefix(commandMap, activePrefix, "reload", new ReloadCommand(this), "regionrental.admin.reload");
        registerCommandWithPrefix(commandMap, activePrefix, "createsign", new CreateSignCommand(this), "regionrental.admin.createsign");
        registerCommandWithPrefix(commandMap, activePrefix, "reset", new ResetCommand(this), "regionrental.admin.reset");
        registerCommandWithPrefix(commandMap, activePrefix, "retrieve", new RetrieveCommand(this), "regionrental.retrieve");
        registerCommandWithPrefix(commandMap, activePrefix, "info", new InfoCommand(this), "regionrental.info");
        registerCommandWithPrefix(commandMap, activePrefix, "list", new ListCommand(this), "regionrental.list");
        registerCommandWithPrefix(commandMap, activePrefix, "extend", new ExtendCommand(this), "regionrental.extend");

        DurationCommand durationCommand = new DurationCommand(this);
        registerCommandWithPrefix(commandMap, activePrefix, "duration", durationCommand, "regionrental.admin.duration");

        registerCommandWithPrefix(commandMap, activePrefix, "remove", new RemoveCommand(this), "regionrental.admin.remove");
        registerCommandWithPrefix(commandMap, activePrefix, "refundhistory", new RefundHistoryCommand(this), "regionrental.admin.refundhistory");
        registerCommandWithPrefix(commandMap, activePrefix, "verify", new VerifyCommand(this), "regionrental.admin.verify");

        OverrideCommand overrideCommand = new OverrideCommand(this);
        registerCommandWithPrefix(commandMap, activePrefix, "override", overrideCommand, "regionrental.admin.override");

        // Log final status
        logPrefixStatus(configuredPrefix);

        // Update /rr command description to show active prefix
        updateHelpCommandDescription();
    }

    /**
     * Determines the active prefix to use based on conflicts
     */
    private String determineActivePrefix(CommandMap commandMap, String configuredPrefix) {
        // If configured prefix has no conflicts, use it
        if (!checkPrefixConflicts(commandMap, configuredPrefix)) {
            return configuredPrefix;
        }

        // Configured prefix has conflicts, log warning
        getLogger().warning("Custom prefix '" + configuredPrefix + "' conflicts with existing commands!");
        getLogger().warning("Falling back to default 'rr' prefix due to conflicts...");
        usingFallback = true;

        // Check if 'rr' has conflicts
        if (!checkPrefixConflicts(commandMap, "rr")) {
            return "rr";
        }

        // Both configured and 'rr' have conflicts, generate suffixed version
        getLogger().warning("Fallback prefix 'rr' also has conflicts!");
        getLogger().warning("Generating auto-suffixed prefix...");
        return generateSuffixedPrefix(commandMap, "rr");
    }

    /**
     * Checks if a prefix would conflict with existing commands
     */
    private boolean checkPrefixConflicts(CommandMap commandMap, String prefix) {
        // Check main command
        if (commandMap.getCommand(prefix) != null) {
            return true;
        }

        // Check all subcommands
        String[] subcommands = {
            "reload", "createsign", "reset", "retrieve", "info", "list",
            "extend", "duration", "remove", "refundhistory", "verify", "override"
        };

        for (String sub : subcommands) {
            String fullCommand = prefix + sub;
            if (commandMap.getCommand(fullCommand) != null) {
                getLogger().warning("  Conflict detected: /" + fullCommand);
                return true;
            }
        }

        return false;
    }

    /**
     * Generates a suffix-based prefix (rr1, rr2, etc.) until no conflicts exist
     */
    private String generateSuffixedPrefix(CommandMap commandMap, String basePrefix) {
        for (int i = 1; i <= 99; i++) {
            String suffixedPrefix = basePrefix + i;
            if (!checkPrefixConflicts(commandMap, suffixedPrefix)) {
                getLogger().info("Using auto-generated prefix: '" + suffixedPrefix + "'");
                return suffixedPrefix;
            }
        }

        // Fallback to a random string if all numbers are taken (very unlikely)
        String randomPrefix = basePrefix + System.currentTimeMillis() % 1000;
        getLogger().warning("Using randomized prefix: '" + randomPrefix + "'");
        return randomPrefix;
    }

    /**
     * Registers a single command with the given prefix
     */
    private void registerCommandWithPrefix(CommandMap commandMap, String prefix, String subcommand,
                                           org.bukkit.command.CommandExecutor executor, String permission) {
        String commandName = subcommand.isEmpty() ? prefix : prefix + subcommand;

        // Create a custom command instance
        DynamicCommand cmd = new DynamicCommand(commandName, this);
        cmd.setExecutor(executor);
        cmd.setPermission(permission);

        // Only set description for main command to appear in /help
        // Subcommands get no description so they don't clutter /help menu
        if (subcommand.isEmpty()) {
            // Main help command - show in /help with active prefix
            cmd.setDescription("RegionRental (prefix: " + prefix + ") - Type /" + prefix + " for help");
            cmd.setUsage("/" + prefix);
        } else {
            // Subcommands - empty description keeps them out of /help
            cmd.setDescription("");
            cmd.setUsage("/" + commandName);
        }

        // Register with CommandMap
        if (commandMap.register(getName(), cmd)) {
            registeredCommands.add(commandName);
            if (configManager.isDebug()) {
                getLogger().info("  Registered: /" + commandName);
            }
        } else {
            getLogger().warning("  Failed to register: /" + commandName);
        }
    }

    /**
     * Logs the final prefix status to console
     */
    private void logPrefixStatus(String configuredPrefix) {
        if (activePrefix.equals(configuredPrefix) && !usingFallback) {
            getLogger().info("✓ Using custom prefix '" + activePrefix + "' for all commands");
        } else if (usingFallback && activePrefix.equals("rr")) {
            getLogger().warning("⚠ Custom prefix '" + configuredPrefix + "' had conflicts");
            getLogger().info("✓ Using fallback prefix 'rr'");
        } else {
            getLogger().warning("⚠ Both custom prefix '" + configuredPrefix + "' and 'rr' had conflicts");
            getLogger().info("✓ Using auto-generated prefix '" + activePrefix + "'");
        }

        getLogger().info("Commands are now available! Example: /" + activePrefix + "info");
    }

    /**
     * Updates the /rr command description to show the active prefix
     * This ensures /help displays the correct prefix players should use
     */
    private void updateHelpCommandDescription() {
        org.bukkit.command.PluginCommand rrCommand = getCommand("rr");
        if (rrCommand != null) {
            String description;
            if (activePrefix.equals("rr")) {
                // Using default rr prefix
                description = "RegionRental - Rent WorldGuard regions. Type /rr for help";
            } else {
                // Using custom prefix - update description to show it
                description = "RegionRental (active prefix: /" + activePrefix + ") - Type /" + activePrefix + " for help";
            }
            rrCommand.setDescription(description);

            if (configManager.isDebug()) {
                getLogger().info("Updated /rr command description: " + description);
            }
        }
    }

    /**
     * Returns the currently active command prefix
     */
    public String getActivePrefix() {
        return activePrefix != null ? activePrefix : "rr";
    }
    
    private void registerAliasesIfPossible() {
        // Check if aliases are enabled in config
        if (!getConfig().getBoolean("commands.enable-aliases", true)) {
            getLogger().info("Command aliases disabled in config");
            return;
        }
        
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                getLogger().warning("Could not access command map for aliases");
                return;
            }
            
            // List of aliases to try registering
            String[][] aliasesToTry = {
                {"reload", "rrreload"},
                {"createsign", "rrcreatesign"},
                {"reset", "rrreset"},
                {"retime", "rrretime"},
                {"retrieve", "rrretrieve"},
                {"info", "rrinfo"},
                {"list", "rrlist"},
                {"extend", "rrextend"},
                {"duration", "rrduration"},
                {"remove", "rrremove"},
                {"verify", "rrverify"}
            };
            
            boolean allAliasesRegistered = true;
            
            for (String[] alias : aliasesToTry) {
                String shortCommand = alias[0];
                String fullCommand = alias[1];
                
                // Check if command already exists
                if (commandMap.getCommand(shortCommand) != null) {
                    getLogger().info("Alias /" + shortCommand + " conflicts with existing command, skipping");
                    allAliasesRegistered = false;
                    continue;
                }
                
                // Try to register the alias
                Command command = getCommand(fullCommand);
                if (command != null) {
                    commandMap.register(getName(), new CommandAlias(shortCommand, command));
                    registeredAliases.add(shortCommand);
                    if (configManager.isDebug()) {
                        getLogger().info("Registered alias: /" + shortCommand + " -> /" + fullCommand);
                    }
                }
            }
            
            aliasesEnabled = allAliasesRegistered && !registeredAliases.isEmpty();
            
            if (aliasesEnabled) {
                getLogger().info("All command aliases registered successfully!");
            } else if (!registeredAliases.isEmpty()) {
                getLogger().info("Some aliases registered. Conflicts detected for full alias support.");
            }
            
        } catch (Exception e) {
            getLogger().warning("Could not register command aliases: " + e.getMessage());
        }
    }
    
    private void unregisterAliases() {
        if (registeredAliases.isEmpty()) {
            return;
        }
        
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                return;
            }
            
            // Unregister all aliases
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Command> knownCommands = (java.util.Map<String, Command>) knownCommandsField.get(commandMap);
            
            for (String alias : registeredAliases) {
                knownCommands.remove(alias);
                knownCommands.remove(getName().toLowerCase() + ":" + alias);
            }
            
            registeredAliases.clear();
            
        } catch (Exception e) {
            getLogger().warning("Could not unregister command aliases: " + e.getMessage());
        }
    }
    
    private CommandMap getCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            return null;
        }
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SignInteractListener(this), this);
        // StorageManager also registers itself as a listener
    }
    
    private void startTasks() {
        // Start expiration checker task (runs every minute)
        long checkInterval = configManager.getExpirationCheckInterval() * 20L * 60L; // Convert minutes to ticks
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            expirationManager.checkExpiredRentals();
        }, checkInterval, checkInterval);
        
        // Start sign update task (runs every 30 seconds)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            signManager.updateAllSigns();
        }, 600L, 600L); // 30 seconds
        
        // Auto-save task (runs every 5 minutes)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            rentalManager.saveAllRentals();
            signsConfig.save();
            storageConfig.save();
            if (configManager.isDebug()) {
                getLogger().info("Auto-saved all data");
            }
        }, 6000L, 6000L); // 5 minutes
    }
    
    public void reloadPlugin() {
        // Reload configurations
        reloadConfig();
        configManager.reload();
        signsConfig.reload();
        storageConfig.reload();
        regionsConfig.reload();

        // Reload integration managers
        if (ezChestShopManager != null) {
            ezChestShopManager.reload();
        }

        // Reload data
        rentalManager.loadAllRentals();
        signManager.loadAllSigns();

        // Verify regions if auto-verify enabled
        if (configManager.isAutoVerifyRegions()) {
            regionsConfig.verifyAndRepairRegions();
        }

        // Re-register aliases
        unregisterAliases();
        registerAliasesIfPossible();

        // Update /rr command description in case prefix changed
        updateHelpCommandDescription();

        getLogger().info("Plugin reloaded successfully!");
    }
    
    // Getters
    public static RegionRental getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public SignsConfig getSignsConfig() {
        return signsConfig;
    }
    
    public StorageConfig getStorageConfig() {
        return storageConfig;
    }

    public RegionsConfig getRegionsConfig() {
        return regionsConfig;
    }

    public RentalManager getRentalManager() {
        return rentalManager;
    }
    
    public SignManager getSignManager() {
        return signManager;
    }
    
    public StorageManager getStorageManager() {
        return storageManager;
    }
    
    public ExpirationManager getExpirationManager() {
        return expirationManager;
    }
    
    public WorldGuardManager getWorldGuardManager() {
        return worldGuardManager;
    }

    public WorldEditManager getWorldEditManager() {
        return worldEditManager;
    }

    public EzChestShopManager getEzChestShopManager() {
        return ezChestShopManager;
    }

    public Economy getEconomy() {
        return economy;
    }
    
    public boolean areAliasesEnabled() {
        return aliasesEnabled;
    }
    
    // Inner class for command aliases
    private static class CommandAlias extends Command {
        private final Command original;

        public CommandAlias(String name, Command original) {
            super(name);
            this.original = original;
            this.description = original.getDescription();
            this.usageMessage = original.getUsage();
            this.setPermission(original.getPermission());
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            return original.execute(sender, commandLabel, args);
        }
    }

    // Inner class for dynamic command registration
    private static class DynamicCommand extends Command {
        private final RegionRental plugin;
        private org.bukkit.command.CommandExecutor executor;

        public DynamicCommand(String name, RegionRental plugin) {
            super(name);
            this.plugin = plugin;
        }

        public void setExecutor(org.bukkit.command.CommandExecutor executor) {
            this.executor = executor;
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            if (executor != null) {
                return executor.onCommand(sender, this, commandLabel, args);
            }
            return false;
        }

        @Override
        public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args)
                throws IllegalArgumentException {
            if (executor instanceof org.bukkit.command.TabCompleter) {
                return ((org.bukkit.command.TabCompleter) executor).onTabComplete(sender, this, alias, args);
            }
            return super.tabComplete(sender, alias, args);
        }
    }
}
