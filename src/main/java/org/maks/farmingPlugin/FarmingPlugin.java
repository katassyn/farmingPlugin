package org.maks.farmingPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.maks.farmingPlugin.commands.PlantationCommand;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.listeners.PlantationListeners;
import org.maks.farmingPlugin.managers.EconomyManager;
import org.maks.farmingPlugin.managers.OfflineGrowthManager;
import org.maks.farmingPlugin.managers.PlantationAreaManager;
import org.maks.farmingPlugin.managers.PlantationManager;
import org.maks.farmingPlugin.materials.MaterialManager;

import java.util.logging.Level;

public final class FarmingPlugin extends JavaPlugin {
    
    private DatabaseManager databaseManager;
    private MaterialManager materialManager;
    private EconomyManager economyManager;
    private PlantationManager plantationManager;
    private PlantationAreaManager plantationAreaManager;
    private OfflineGrowthManager offlineGrowthManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        getLogger().info("Initializing Farming Plugin...");
        
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.connect();
            databaseManager.createTables();
            getLogger().info("Database initialized successfully!");
            
            materialManager = new MaterialManager(this);
            getLogger().info("Material system initialized!");
            
            economyManager = new EconomyManager(this);
            getLogger().info("Economy integration initialized!");
            
            plantationManager = new PlantationManager(this, databaseManager);
            getLogger().info("Plantation manager initialized!");
            
            plantationAreaManager = new PlantationAreaManager(this);
            getLogger().info("Plantation area manager initialized!");
            
            offlineGrowthManager = new OfflineGrowthManager(this, plantationManager);
            getLogger().info("Offline growth system initialized!");
            
            getCommand("plantation").setExecutor(new PlantationCommand(this));
            getCommand("plantation").setTabCompleter(new PlantationCommand(this));
            
            getServer().getPluginManager().registerEvents(new PlantationListeners(this), this);
            
            getLogger().info("Farming Plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize plugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling Farming Plugin...");
        
        if (offlineGrowthManager != null) {
            offlineGrowthManager.shutdown();
        }
        
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        
        getLogger().info("Farming Plugin disabled successfully!");
    }

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
}
