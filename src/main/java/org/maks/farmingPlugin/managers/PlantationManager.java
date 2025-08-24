package org.maks.farmingPlugin.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.farms.MaterialDrop;
import org.maks.farmingPlugin.materials.MaterialType;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlantationManager {
    private final FarmingPlugin plugin;
    private final DatabaseManager database;
    private final Gson gson;
    
    private final Map<UUID, List<FarmInstance>> playerFarms;
    private final Map<FarmType, List<MaterialDrop>> farmDrops;
    private final Map<FarmType, Map<MaterialType, Integer>> unlockRequirements;

    public PlantationManager(FarmingPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.gson = new Gson();
        this.playerFarms = new ConcurrentHashMap<>();
        this.farmDrops = new HashMap<>();
        this.unlockRequirements = new HashMap<>();
        
        loadFarmConfigurations();
    }

    private void loadFarmConfigurations() {
        farmDrops.put(FarmType.BERRY_ORCHARDS, Arrays.asList(
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 70.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 2, 25.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 1, 20.0)
        ));

        farmDrops.put(FarmType.MELON_GROVES, Arrays.asList(
            new MaterialDrop(MaterialType.SEED_POUCH, 1, 60.0),
            new MaterialDrop(MaterialType.SEED_POUCH, 2, 30.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 25.0)
        ));

        farmDrops.put(FarmType.FUNGAL_CAVERNS, Arrays.asList(
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 1, 45.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 2, 20.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 3, 8.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 1, 35.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 2, 15.0)
        ));

        farmDrops.put(FarmType.PUMPKIN_PATCHES, Arrays.asList(
            new MaterialDrop(MaterialType.SEED_POUCH, 2, 40.0),
            new MaterialDrop(MaterialType.SEED_POUCH, 3, 15.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 1, 50.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 2, 25.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 3, 8.0)
        ));

        farmDrops.put(FarmType.MYSTIC_GARDENS, Arrays.asList(
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 1, 35.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 2, 20.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 3, 10.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 1, 25.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 2, 12.0)
        ));

        farmDrops.put(FarmType.ANCIENT_MANGROVES, Arrays.asList(
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 1, 18.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 6.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 3, 15.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 3, 12.0)
        ));

        farmDrops.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 1, 10.0),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 2, 4.0),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 3, 1.5),
            new MaterialDrop(MaterialType.ANCIENT_GRAIN, 1, 8.0),
            new MaterialDrop(MaterialType.ANCIENT_GRAIN, 2, 3.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 6.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 3, 2.0)
        ));

        Map<MaterialType, Integer> melonReq = new HashMap<>();
        melonReq.put(MaterialType.PLANT_FIBER, 50);
        melonReq.put(MaterialType.HERBAL_EXTRACT, 10);
        unlockRequirements.put(FarmType.MELON_GROVES, melonReq);

        Map<MaterialType, Integer> fungalReq = new HashMap<>();
        fungalReq.put(MaterialType.SEED_POUCH, 30);
        fungalReq.put(MaterialType.PLANT_FIBER, 15);
        unlockRequirements.put(FarmType.FUNGAL_CAVERNS, fungalReq);
    }

    public void loadPlayerData(UUID playerUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "SELECT * FROM player_plantations WHERE uuid = ?";
                PreparedStatement stmt = database.prepareStatement(sql);
                stmt.setString(1, playerUuid.toString());
                
                ResultSet rs = stmt.executeQuery();
                List<FarmInstance> farms = new ArrayList<>();
                
                while (rs.next()) {
                    FarmType farmType = FarmType.fromId(rs.getString("farm_type"));
                    if (farmType != null) {
                        int instanceId = rs.getInt("instance_id");
                        int level = rs.getInt("level");
                        int efficiency = rs.getInt("efficiency");
                        long lastHarvest = rs.getLong("last_harvest");
                        
                        Map<String, Integer> storedMaterials = loadStoredMaterials(playerUuid, farmType, instanceId);
                        
                        FarmInstance instance = new FarmInstance(playerUuid, farmType, instanceId, 
                                                               level, efficiency, lastHarvest, storedMaterials);
                        farms.add(instance);
                    }
                }
                
                playerFarms.put(playerUuid, farms);
                rs.close();
                stmt.close();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player plantation data for " + playerUuid, e);
            }
        });
    }

    private Map<String, Integer> loadStoredMaterials(UUID playerUuid, FarmType farmType, int instanceId) {
        try {
            String sql = "SELECT stored_materials_json FROM plantation_storage WHERE uuid = ? AND farm_type = ? AND instance_id = ?";
            PreparedStatement stmt = database.prepareStatement(sql);
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, farmType.getId());
            stmt.setInt(3, instanceId);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("stored_materials_json");
                if (json != null && !json.isEmpty()) {
                    Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                    return gson.fromJson(json, type);
                }
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load stored materials", e);
        }
        
        return new HashMap<>();
    }

    public void savePlayerData(UUID playerUuid) {
        List<FarmInstance> farms = playerFarms.get(playerUuid);
        if (farms == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (FarmInstance farm : farms) {
                    String sql = "INSERT INTO player_plantations (uuid, farm_type, instance_id, level, efficiency, last_harvest) " +
                               "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                               "level = VALUES(level), efficiency = VALUES(efficiency), last_harvest = VALUES(last_harvest)";
                    
                    PreparedStatement stmt = database.prepareStatement(sql);
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, farm.getFarmType().getId());
                    stmt.setInt(3, farm.getInstanceId());
                    stmt.setInt(4, farm.getLevel());
                    stmt.setInt(5, farm.getEfficiency());
                    stmt.setLong(6, farm.getLastHarvest());
                    stmt.executeUpdate();
                    stmt.close();

                    saveStoredMaterials(playerUuid, farm);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player plantation data for " + playerUuid, e);
            }
        });
    }

    private void saveStoredMaterials(UUID playerUuid, FarmInstance farm) throws SQLException {
        String json = gson.toJson(farm.getStoredMaterials());
        
        String sql = "INSERT INTO plantation_storage (uuid, farm_type, instance_id, stored_materials_json) " +
                   "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE stored_materials_json = VALUES(stored_materials_json)";
        
        PreparedStatement stmt = database.prepareStatement(sql);
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, farm.getFarmType().getId());
        stmt.setInt(3, farm.getInstanceId());
        stmt.setString(4, json);
        stmt.executeUpdate();
        stmt.close();
    }

    public List<FarmInstance> getPlayerFarms(UUID playerUuid) {
        return playerFarms.getOrDefault(playerUuid, new ArrayList<>());
    }

    public FarmInstance getFarmInstance(UUID playerUuid, FarmType farmType, int instanceId) {
        return getPlayerFarms(playerUuid).stream()
                .filter(farm -> farm.getFarmType() == farmType && farm.getInstanceId() == instanceId)
                .findFirst()
                .orElse(null);
    }

    public boolean canUnlockFarm(UUID playerUuid, FarmType farmType) {
        if (farmType == FarmType.BERRY_ORCHARDS) return true;
        
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        if (requirements == null) return true;
        
        return true;
    }

    public FarmInstance createFarmInstance(UUID playerUuid, FarmType farmType, int instanceId, Location location) {
        List<FarmInstance> farms = playerFarms.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        
        FarmInstance instance = new FarmInstance(playerUuid, farmType, instanceId);
        instance.setLocation(location);
        farms.add(instance);
        
        return instance;
    }

    public void processFarmHarvest(FarmInstance farm) {
        if (!farm.isReadyForHarvest() || !farm.canStoreMore()) {
            return;
        }

        List<MaterialDrop> drops = farmDrops.get(farm.getFarmType());
        if (drops == null) return;

        Random random = new Random();
        double levelMultiplier = 1.0 + (farm.getLevel() - 1) * 0.2;

        for (MaterialDrop drop : drops) {
            double chance = drop.getRate() * levelMultiplier;
            if (random.nextDouble() * 100.0 <= chance) {
                String materialKey = drop.getMaterialType().getId() + "_tier_" + drop.getTier();
                farm.addStoredMaterial(materialKey, 1);
            }
        }

        farm.setLastHarvest(System.currentTimeMillis());
        
        if (farm.getTotalStoredItems() >= farm.getFarmType().getStorageLimit()) {
            return;
        }
    }

    public List<MaterialDrop> getFarmDrops(FarmType farmType) {
        return farmDrops.getOrDefault(farmType, new ArrayList<>());
    }
}