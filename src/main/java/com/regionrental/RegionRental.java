package com.regionrental;

import com.regionrental.commands.*;
import com.regionrental.config.ConfigManager;
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
    private RentalManager rentalManager;
    private SignManager signManager;
    private StorageManager storageManager;
    private ExpirationManager expirationManager;
    private WorldGuardManager worldGuardManager;
    private WorldEditManager worldEditManager;
    
    // Economy
    private Economy economy;
    
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
        // Main command handler
        getCommand("rr").setExecutor(new RRCommand(this));

        // Register all subcommands with rr prefix
        getCommand("rrreload").setExecutor(new ReloadCommand(this));
        getCommand("rrcreatesign").setExecutor(new CreateSignCommand(this));
        getCommand("rrreset").setExecutor(new ResetCommand(this));
        getCommand("rrretrieve").setExecutor(new RetrieveCommand(this));
        getCommand("rrinfo").setExecutor(new InfoCommand(this));
        getCommand("rrlist").setExecutor(new ListCommand(this));
        getCommand("rrextend").setExecutor(new ExtendCommand(this));
        getCommand("rrduration").setExecutor(new DurationCommand(this));
        getCommand("rrremove").setExecutor(new RemoveCommand(this));
        getCommand("rrrefundhistory").setExecutor(new RefundHistoryCommand(this));
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
                {"remove", "rrremove"}
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
        
        // Reload data
        rentalManager.loadAllRentals();
        signManager.loadAllSigns();
        
        // Re-register aliases
        unregisterAliases();
        registerAliasesIfPossible();
        
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
}
