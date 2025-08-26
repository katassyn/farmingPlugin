package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OfflineGrowthManager {
    private final FarmingPlugin plugin;
    private final PlantationManager plantationManager;
    private final ConcurrentMap<UUID, Long> playerLastSeen;
    private BukkitRunnable growthTask;

    public OfflineGrowthManager(FarmingPlugin plugin, PlantationManager plantationManager) {
        this.plugin = plugin;
        this.plantationManager = plantationManager;
        this.playerLastSeen = new ConcurrentHashMap<>();
        startGrowthTask();
    }

    private void startGrowthTask() {
        growthTask = new BukkitRunnable() {
            @Override
            public void run() {
                processAllFarmGrowth();
            }
        };
        
        growthTask.runTaskTimerAsynchronously(plugin, 20L * 60L, 20L * 60L);
    }

    public void processAllFarmGrowth() {
        for (UUID playerId : playerLastSeen.keySet()) {
            processPlayerFarmGrowth(playerId);
        }
    }

    private void processPlayerFarmGrowth(UUID playerId) {
        try {
            for (FarmInstance farm : plantationManager.getPlayerFarms(playerId)) {
                processOfflineGrowth(farm);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing farm growth for player " + playerId + ": " + e.getMessage());
        }
    }

    public void processOfflineGrowth(FarmInstance farm) {
        // Growth is calculated dynamically during player harvests.
    }

    public void onPlayerJoin(UUID playerId) {
        playerLastSeen.put(playerId, System.currentTimeMillis());
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (FarmInstance farm : plantationManager.getPlayerFarms(playerId)) {
                    processOfflineGrowth(farm);
                }
                
                plantationManager.savePlayerData(playerId);
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing offline growth for player " + playerId + ": " + e.getMessage());
            }
        });
    }

    public void onPlayerQuit(UUID playerId) {
        playerLastSeen.put(playerId, System.currentTimeMillis());
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plantationManager.savePlayerData(playerId);
            } catch (Exception e) {
                plugin.getLogger().warning("Error saving player data on quit for " + playerId + ": " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        if (growthTask != null && !growthTask.isCancelled()) {
            growthTask.cancel();
        }
        
        for (UUID playerId : playerLastSeen.keySet()) {
            try {
                for (FarmInstance farm : plantationManager.getPlayerFarms(playerId)) {
                    processOfflineGrowth(farm);
                }
                plantationManager.savePlayerData(playerId);
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing final growth for player " + playerId + ": " + e.getMessage());
            }
        }
    }

    public long getPlayerLastSeen(UUID playerId) {
        return playerLastSeen.getOrDefault(playerId, System.currentTimeMillis());
    }

    public void registerPlayer(UUID playerId) {
        playerLastSeen.putIfAbsent(playerId, System.currentTimeMillis());
    }
}