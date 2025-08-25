package org.maks.farmingPlugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.farmingPlugin.commands.PlantationCommand;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.listeners.PlantationListeners;
import org.maks.farmingPlugin.managers.*;
import org.maks.farmingPlugin.materials.MaterialManager;

import java.util.logging.Level;

public final class FarmingPlugin extends JavaPlugin {
    
    private DatabaseManager databaseManager;
    private MaterialManager materialManager;
    private EconomyManager economyManager;
    private PlantationManager plantationManager;
    private PlantationAreaManager plantationAreaManager;
    private OfflineGrowthManager offlineGrowthManager;
    private HologramManager hologramManager;
    
    private static FarmingPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("    Farming Plugin v1.0 - Starting");
        getLogger().info("═══════════════════════════════════════");
        
        try {
            // Initialize database first
            initializeDatabase();
            
            // Initialize managers
            initializeManagers();
            
            // Register commands and listeners
            registerCommands();
            registerListeners();
            
            // Load online players data (for reload support)
            loadOnlinePlayersData();
            
            // Start metrics if enabled
            if (getConfig().getBoolean("metrics.enabled", true)) {
                startMetrics();
            }
            
            getLogger().info("═══════════════════════════════════════");
            getLogger().info("    Farming Plugin - Ready!");
            getLogger().info("    " + getServer().getOnlinePlayers().size() + " players loaded");
            getLogger().info("═══════════════════════════════════════");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize plugin!", e);
            getLogger().severe("═══════════════════════════════════════");
            getLogger().severe("    CRITICAL ERROR - DISABLING PLUGIN");
            getLogger().severe("═══════════════════════════════════════");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("    Farming Plugin - Shutting down");
        getLogger().info("═══════════════════════════════════════");
        
        // Save all online players data
        if (plantationManager != null) {
            getServer().getOnlinePlayers().forEach(player -> {
                plantationManager.savePlayerData(player.getUniqueId());
            });
        }
        
        // Shutdown managers in correct order
        if (offlineGrowthManager != null) {
            getLogger().info("Shutting down offline growth manager...");
            offlineGrowthManager.shutdown();
        }
        
        if (hologramManager != null) {
            getLogger().info("Cleaning up holograms...");
            hologramManager.cleanup();
        }
        
        // Close database connection
        if (databaseManager != null) {
            getLogger().info("Closing database connection...");
            databaseManager.disconnect();
        }
        
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("    Farming Plugin - Disabled");
        getLogger().info("═══════════════════════════════════════");
    }

    private void initializeDatabase() throws Exception {
        getLogger().info("Initializing database...");
        
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        databaseManager.createTables();
        
        getLogger().info("✔ Database initialized successfully!");
    }

    private void initializeManagers() throws Exception {
        // Material Manager
        getLogger().info("Initializing material system...");
        materialManager = new MaterialManager(this);
        getLogger().info("✔ Material system initialized!");
        
        // Economy Manager
        getLogger().info("Initializing economy integration...");
        economyManager = new EconomyManager(this);
        
        if (economyManager.isEconomyEnabled()) {
            getLogger().info("✔ Economy integration enabled with " + economyManager.getEconomy().getName());
        } else {
            getLogger().warning("⚠ Economy integration disabled - Vault/Economy plugin not found!");
        }
        
        // Plantation Manager
        getLogger().info("Initializing plantation manager...");
        plantationManager = new PlantationManager(this, databaseManager);
        getLogger().info("✔ Plantation manager initialized!");
        
        // Plantation Area Manager
        getLogger().info("Initializing area manager...");
        plantationAreaManager = new PlantationAreaManager(this);
        getLogger().info("✔ Area manager initialized!");
        
        // Offline Growth Manager
        getLogger().info("Initializing offline growth system...");
        offlineGrowthManager = new OfflineGrowthManager(this, plantationManager);
        getLogger().info("✔ Offline growth system initialized!");
        
        // Hologram Manager (optional)
        if (getConfig().getBoolean("plantations.holograms.enabled", true)) {
            getLogger().info("Initializing hologram system...");
            
            // Check for hologram dependencies
            if (checkHologramDependencies()) {
                hologramManager = new HologramManager(this);
                getLogger().info("✔ Hologram system initialized!");
            } else {
                getLogger().warning("⚠ Hologram system disabled - missing dependencies!");
            }
        } else {
            getLogger().info("Hologram system disabled in config");
        }
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");
        
        PlantationCommand plantationCommand = new PlantationCommand(this);
        getCommand("plantation").setExecutor(plantationCommand);
        getCommand("plantation").setTabCompleter(plantationCommand);
        
        getLogger().info("✔ Commands registered!");
    }

    private void registerListeners() {
        getLogger().info("Registering event listeners...");
        
        getServer().getPluginManager().registerEvents(new PlantationListeners(this), this);
        
        getLogger().info("✔ Event listeners registered!");
    }

    private void loadOnlinePlayersData() {
        // Load data for players already online (in case of reload)
        getServer().getOnlinePlayers().forEach(player -> {
            getLogger().info("Loading data for " + player.getName() + "...");
            plantationManager.loadPlayerData(player.getUniqueId());
            offlineGrowthManager.onPlayerJoin(player.getUniqueId());
        });
    }

    private boolean checkHologramDependencies() {
        // Holograms work with vanilla armor stands, no external dependencies needed
        return true;
    }

    private void startMetrics() {
        // Placeholder for metrics implementation (bStats)
        getLogger().info("Metrics enabled");
    }

    // Reload configuration
    public void reloadConfiguration() {
        reloadConfig();
        
        // Reload manager configurations if needed
        if (plantationManager != null) {
            // Reload farm configurations
            getLogger().info("Configuration reloaded!");
        }
    }

    // Getters for managers
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MaterialManager getMaterialManager() {
        return materialManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlantationManager getPlantationManager() {
        return plantationManager;
    }

    public OfflineGrowthManager getOfflineGrowthManager() {
        return offlineGrowthManager;
    }

    public PlantationAreaManager getPlantationAreaManager() {
        return plantationAreaManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public static FarmingPlugin getInstance() {
        return instance;
    }

    // Utility methods
    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void logError(String message, Exception e) {
        getLogger().log(Level.SEVERE, message, e);
        
        if (getConfig().getBoolean("debug", false)) {
            e.printStackTrace();
        }
    }

    // API methods for other plugins
    public boolean hasFarm(java.util.UUID playerUuid, String farmType) {
        return plantationManager.getPlayerFarms(playerUuid).stream()
            .anyMatch(farm -> farm.getFarmType().getId().equals(farmType));
    }

    public int getFarmLevel(java.util.UUID playerUuid, String farmType) {
        return plantationManager.getPlayerFarms(playerUuid).stream()
            .filter(farm -> farm.getFarmType().getId().equals(farmType))
            .mapToInt(farm -> farm.getLevel())
            .max()
            .orElse(0);
    }

    public long getTotalMaterialsProduced(java.util.UUID playerUuid) {
        return plantationManager.getPlayerFarms(playerUuid).stream()
            .mapToLong(farm -> farm.getTotalMaterialsProduced())
            .sum();
    }

    // Scheduled tasks
    private void startScheduledTasks() {
        // Auto-save task
        if (getConfig().getBoolean("auto_save.enabled", true)) {
            int interval = getConfig().getInt("auto_save.interval_minutes", 5) * 20 * 60;
            
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                debug("Running auto-save...");
                
                getServer().getOnlinePlayers().forEach(player -> {
                    plantationManager.savePlayerData(player.getUniqueId());
                });
                
                debug("Auto-save completed!");
            }, interval, interval);
        }

        // Statistics update task
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            getServer().getOnlinePlayers().forEach(player -> {
                databaseManager.updatePlayerStats(player.getUniqueId(), "play_time_minutes", 1);
            });
        }, 20L * 60, 20L * 60); // Every minute
    }
}
