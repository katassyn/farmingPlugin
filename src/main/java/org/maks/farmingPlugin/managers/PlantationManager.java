package org.maks.farmingPlugin.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.database.DatabaseManager;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.farms.MaterialDrop;
import org.maks.farmingPlugin.materials.MaterialManager;
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
    private final Map<UUID, PlayerSettings> playerSettings;

    public PlantationManager(FarmingPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.gson = new Gson();
        this.playerFarms = new ConcurrentHashMap<>();
        this.farmDrops = new HashMap<>();
        this.unlockRequirements = new HashMap<>();
        this.playerSettings = new ConcurrentHashMap<>();
        
        loadFarmConfigurations();
        startAutoSaveTask();
    }

    private void loadFarmConfigurations() {
        // Load farm drops from config
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

        // Load unlock requirements
        Map<MaterialType, Integer> melonReq = new HashMap<>();
        melonReq.put(MaterialType.PLANT_FIBER, 50);
        melonReq.put(MaterialType.HERBAL_EXTRACT, 10);
        unlockRequirements.put(FarmType.MELON_GROVES, melonReq);

        Map<MaterialType, Integer> fungalReq = new HashMap<>();
        fungalReq.put(MaterialType.SEED_POUCH, 30);
        fungalReq.put(MaterialType.PLANT_FIBER, 15);
        unlockRequirements.put(FarmType.FUNGAL_CAVERNS, fungalReq);

        Map<MaterialType, Integer> pumpkinReq = new HashMap<>();
        pumpkinReq.put(MaterialType.MUSHROOM_SPORES, 25);
        pumpkinReq.put(MaterialType.COMPOST_DUST, 20);
        unlockRequirements.put(FarmType.PUMPKIN_PATCHES, pumpkinReq);

        Map<MaterialType, Integer> mysticReq = new HashMap<>();
        mysticReq.put(MaterialType.SEED_POUCH, 20);
        mysticReq.put(MaterialType.COMPOST_DUST, 15);
        mysticReq.put(MaterialType.MUSHROOM_SPORES, 10);
        unlockRequirements.put(FarmType.MYSTIC_GARDENS, mysticReq);

        Map<MaterialType, Integer> mangroveReq = new HashMap<>();
        mangroveReq.put(MaterialType.HERBAL_EXTRACT, 25);
        mangroveReq.put(MaterialType.BEESWAX_CHUNK, 15);
        mangroveReq.put(MaterialType.COMPOST_DUST, 10);
        unlockRequirements.put(FarmType.ANCIENT_MANGROVES, mangroveReq);

        Map<MaterialType, Integer> desertReq = new HashMap<>();
        desertReq.put(MaterialType.DRUIDIC_ESSENCE, 10);
        desertReq.put(MaterialType.BEESWAX_CHUNK, 15);
        desertReq.put(MaterialType.HERBAL_EXTRACT, 8);
        unlockRequirements.put(FarmType.DESERT_SANCTUARIES, desertReq);
    }

    public void loadPlayerData(UUID playerUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Load farms
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
                        int totalHarvests = rs.getInt("total_harvests");
                        int exp = rs.getInt("exp");
                        
                        // Load stored materials
                        Map<String, Integer> storedMaterials = loadStoredMaterials(playerUuid, farmType, instanceId);
                        
                        // Get anchor location
                        Location location = plugin.getPlantationAreaManager()
                            .getOrCreateFarmAnchor(playerUuid, farmType, instanceId);
                        
                        FarmInstance instance = new FarmInstance(playerUuid, farmType, instanceId, 
                                                               level, efficiency, lastHarvest, storedMaterials);
                        instance.setLocation(location);
                        instance.setTotalHarvests(totalHarvests);
                        instance.setExp(exp);
                        
                        farms.add(instance);
                        
                        // Create hologram if enabled
                        if (plugin.getHologramManager() != null) {
                            plugin.getHologramManager().updateHologram(instance);
                        }
                    }
                }
                
                playerFarms.put(playerUuid, farms);
                rs.close();
                stmt.close();
                
                // Load player settings
                loadPlayerSettings(playerUuid);
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player plantation data for " + playerUuid, e);
            }
        });
    }

    private void loadPlayerSettings(UUID playerUuid) {
        PlayerSettings settings = new PlayerSettings();
        
        settings.autoCollect = database.getPlayerBooleanSetting(playerUuid, "auto_collect_enabled", false);
        settings.hologramsEnabled = database.getPlayerBooleanSetting(playerUuid, "hologram_enabled", true);
        settings.notificationsEnabled = database.getPlayerBooleanSetting(playerUuid, "notifications_enabled", true);
        settings.particleEffectsEnabled = database.getPlayerBooleanSetting(playerUuid, "particle_effects_enabled", true);
        settings.dropToInventory = database.getPlayerBooleanSetting(playerUuid, "drop_to_inventory", false);
        
        playerSettings.put(playerUuid, settings);
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
                    // Save farm data
                    String sql = "INSERT INTO player_plantations (uuid, farm_type, instance_id, level, efficiency, last_harvest, total_harvests, exp) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                               "level = VALUES(level), efficiency = VALUES(efficiency), last_harvest = VALUES(last_harvest), " +
                               "total_harvests = VALUES(total_harvests), exp = VALUES(exp)";
                    
                    PreparedStatement stmt = database.prepareStatement(sql);
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, farm.getFarmType().getId());
                    stmt.setInt(3, farm.getInstanceId());
                    stmt.setInt(4, farm.getLevel());
                    stmt.setInt(5, farm.getEfficiency());
                    stmt.setLong(6, farm.getLastHarvest());
                    stmt.setInt(7, farm.getTotalHarvests());
                    stmt.setInt(8, farm.getExp());
                    stmt.executeUpdate();
                    stmt.close();

                    // Save stored materials
                    saveStoredMaterials(playerUuid, farm);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player plantation data for " + playerUuid, e);
            }
        });
    }

    private void saveStoredMaterials(UUID playerUuid, FarmInstance farm) throws SQLException {
        String json = gson.toJson(farm.getStoredMaterials());
        PlayerSettings settings = playerSettings.get(playerUuid);
        boolean autoCollect = settings != null && settings.autoCollect;
        
        String sql = "INSERT INTO plantation_storage (uuid, farm_type, instance_id, stored_materials_json, auto_collect) " +
                   "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                   "stored_materials_json = VALUES(stored_materials_json), auto_collect = VALUES(auto_collect)";
        
        PreparedStatement stmt = database.prepareStatement(sql);
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, farm.getFarmType().getId());
        stmt.setInt(3, farm.getInstanceId());
        stmt.setString(4, json);
        stmt.setBoolean(5, autoCollect);
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

    /**
     * Check if player can unlock a farm type
     */
    public boolean canUnlockFarm(UUID playerUuid, FarmType farmType) {
        if (farmType == FarmType.BERRY_ORCHARDS) return true;
        
        // Check if already unlocked
        if (database.isFarmUnlocked(playerUuid, farmType.getId())) {
            return true;
        }
        
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        if (requirements == null) return true;
        
        // Check material requirements
        for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
            MaterialType materialType = req.getKey();
            int requiredAmount = req.getValue();
            
            // Check tier 1 materials by default
            int playerAmount = database.getPlayerMaterialAmount(playerUuid, materialType.getId(), 1);
            
            if (playerAmount < requiredAmount) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Unlock a farm type for player
     */
    public boolean unlockFarm(UUID playerUuid, FarmType farmType) {
        if (!canUnlockFarm(playerUuid, farmType)) {
            return false;
        }
        
        Player player = Bukkit.getPlayer(playerUuid);
        
        // Check money
        if (!plugin.getEconomyManager().hasBalance(playerUuid, farmType.getUnlockCost())) {
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Insufficient funds! You need " + 
                    plugin.getEconomyManager().formatMoney(farmType.getUnlockCost()));
            }
            return false;
        }
        
        // Deduct materials
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        Map<String, Integer> usedMaterials = new HashMap<>();
        
        if (requirements != null) {
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                MaterialType materialType = req.getKey();
                int requiredAmount = req.getValue();
                
                // Deduct from database
                database.updatePlayerMaterial(playerUuid, materialType.getId(), 1, -requiredAmount);
                usedMaterials.put(materialType.getId() + "_tier_1", requiredAmount);
                
                // Remove from player inventory if online
                if (player != null) {
                    removeMaterialFromInventory(player, materialType, 1, requiredAmount);
                }
            }
        }
        
        // Deduct money
        plugin.getEconomyManager().withdrawMoney(playerUuid, farmType.getUnlockCost());
        
        // Save unlock to database
        String materialsJson = gson.toJson(usedMaterials);
        database.saveFarmUnlock(playerUuid, farmType.getId(), farmType.getUnlockCost(), materialsJson);
        
        // Update stats
        database.updatePlayerStats(playerUuid, "total_farms_created", 1);
        database.updatePlayerStats(playerUuid, "total_money_spent", (long) farmType.getUnlockCost());
        
        if (player != null) {
            player.sendMessage(ChatColor.GREEN + "Successfully unlocked " + farmType.getDisplayName() + "!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        
        return true;
    }

    private void removeMaterialFromInventory(Player player, MaterialType materialType, int tier, int amount) {
        MaterialManager mm = plugin.getMaterialManager();
        int remaining = amount;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            
            if (mm.isFarmingMaterial(item) && 
                mm.getMaterialType(item) == materialType && 
                mm.getMaterialTier(item) == tier) {
                
                int stackAmount = item.getAmount();
                if (stackAmount <= remaining) {
                    item.setAmount(0);
                    remaining -= stackAmount;
                } else {
                    item.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
                
                if (remaining <= 0) break;
            }
        }
        
        player.updateInventory();
    }

    public FarmInstance createFarmInstance(UUID playerUuid, FarmType farmType, int instanceId, Location location) {
        List<FarmInstance> farms = playerFarms.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        
        FarmInstance instance = new FarmInstance(playerUuid, farmType, instanceId);
        instance.setLocation(location);
        farms.add(instance);
        
        // Create hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(instance);
        }
        
        // Update stats
        database.updatePlayerStats(playerUuid, "total_farms_created", 1);
        
        return instance;
    }

    /**
     * Process farm harvest with auto-drop functionality
     */
    public void processFarmHarvest(FarmInstance farm) {
        if (!farm.isReadyForHarvest()) {
            return;
        }

        List<MaterialDrop> drops = farmDrops.get(farm.getFarmType());
        if (drops == null) return;

        Random random = new Random();
        double levelMultiplier = 1.0 + (farm.getLevel() - 1) * 0.2;
        double efficiencyBonus = 1.0 + (farm.getEfficiency() - 1) * 0.1;
        
        Map<String, Integer> harvestedMaterials = new HashMap<>();
        int totalHarvested = 0;

        for (MaterialDrop drop : drops) {
            double chance = drop.getRate() * levelMultiplier * efficiencyBonus;
            if (random.nextDouble() * 100.0 <= chance) {
                String materialKey = drop.getMaterialType().getId() + "_tier_" + drop.getTier();
                int amount = 1 + (random.nextDouble() < 0.1 ? 1 : 0); // 10% chance for double drop
                
                harvestedMaterials.put(materialKey, harvestedMaterials.getOrDefault(materialKey, 0) + amount);
                totalHarvested += amount;
            }
        }

        // Check if storage can hold all materials
        boolean canStore = farm.getTotalStoredItems() + totalHarvested <= farm.getFarmType().getStorageLimit();
        
        if (canStore) {
            // Add to storage
            for (Map.Entry<String, Integer> entry : harvestedMaterials.entrySet()) {
                farm.addStoredMaterial(entry.getKey(), entry.getValue());
            }
        } else {
            // Auto-drop excess materials
            Player player = Bukkit.getPlayer(farm.getOwnerId());
            PlayerSettings settings = playerSettings.get(farm.getOwnerId());
            
            if (settings != null && settings.autoCollect) {
                // Drop all stored materials plus new harvest
                Map<String, Integer> allMaterials = new HashMap<>(farm.getStoredMaterials());
                for (Map.Entry<String, Integer> entry : harvestedMaterials.entrySet()) {
                    allMaterials.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                
                if (player != null && player.isOnline()) {
                    dropMaterials(player, farm.getLocation(), allMaterials, settings.dropToInventory);
                    farm.clearStoredMaterials();
                    
                    if (settings.notificationsEnabled) {
                        player.sendMessage(ChatColor.GREEN + "Auto-collected " + 
                            allMaterials.values().stream().mapToInt(Integer::intValue).sum() + 
                            " materials from " + farm.getFarmType().getDisplayName() + "!");
                    }
                }
            } else {
                // Just add what fits
                int spaceLeft = farm.getFarmType().getStorageLimit() - farm.getTotalStoredItems();
                int added = 0;
                
                for (Map.Entry<String, Integer> entry : harvestedMaterials.entrySet()) {
                    int toAdd = Math.min(entry.getValue(), spaceLeft - added);
                    if (toAdd > 0) {
                        farm.addStoredMaterial(entry.getKey(), toAdd);
                        added += toAdd;
                    }
                }
            }
        }

        // Update harvest time and stats
        farm.setLastHarvest(System.currentTimeMillis());
        farm.incrementHarvests();
        farm.addExp(10 + totalHarvested);
        
        // Check for level up
        checkLevelUp(farm);
        
        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farm);
            
            if (totalHarvested > 0) {
                plugin.getHologramManager().showHarvestAnimation(farm.getLocation(), totalHarvested);
            }
        }
        
        // Log harvest
        String materialsJson = gson.toJson(harvestedMaterials);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "CALL ProcessHarvest(?, ?, ?, ?)";
                PreparedStatement stmt = database.prepareStatement(sql);
                stmt.setString(1, farm.getOwnerId().toString());
                stmt.setString(2, farm.getFarmType().getId());
                stmt.setInt(3, farm.getInstanceId());
                stmt.setString(4, materialsJson);
                stmt.execute();
                stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not log harvest: " + e.getMessage());
            }
        });
    }

    /**
     * Drop materials for player
     */
    public void dropMaterials(Player player, Location location, Map<String, Integer> materials, boolean toInventory) {
        MaterialManager mm = plugin.getMaterialManager();
        Location dropLoc = location != null ? location.clone().add(0.5, 1, 0.5) : player.getLocation();
        
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String[] parts = entry.getKey().split("_tier_");
            if (parts.length == 2) {
                MaterialType materialType = MaterialType.fromId(parts[0]);
                int tier = Integer.parseInt(parts[1]);
                int amount = entry.getValue();
                
                if (materialType != null && amount > 0) {
                    ItemStack item = mm.createMaterial(materialType, tier, amount);
                    
                    if (toInventory) {
                        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                        for (ItemStack overflowItem : overflow.values()) {
                            player.getWorld().dropItem(dropLoc, overflowItem);
                        }
                    } else {
                        double radius = plugin.getConfig().getDouble("plantations.drop.radius", 0.6);
                        Location finalDropLoc = dropLoc.clone().add(
                            (Math.random() - 0.5) * radius * 2,
                            0,
                            (Math.random() - 0.5) * radius * 2
                        );
                        player.getWorld().dropItem(finalDropLoc, item);
                    }
                }
            }
        }
    }

    /**
     * Check and process level up
     */
    private void checkLevelUp(FarmInstance farm) {
        int currentLevel = farm.getLevel();
        int expNeeded = getExpForLevel(currentLevel + 1);
        
        if (farm.getExp() >= expNeeded && currentLevel < 10) {
            farm.setLevel(currentLevel + 1);
            farm.setExp(farm.getExp() - expNeeded);
            
            Player player = Bukkit.getPlayer(farm.getOwnerId());
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "⬆ " + farm.getFarmType().getDisplayName() + 
                                 " leveled up to " + farm.getLevel() + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().showLevelUpAnimation(farm.getLocation(), farm.getLevel());
            }
        }
    }

    private int getExpForLevel(int level) {
        return level * 100 + (level - 1) * 50;
    }

    public List<MaterialDrop> getFarmDrops(FarmType farmType) {
        return farmDrops.getOrDefault(farmType, new ArrayList<>());
    }

    /**
     * Start auto-save task
     */
    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : playerFarms.keySet()) {
                savePlayerData(uuid);
            }
        }, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
    }

    /**
     * Get unlock requirements display
     */
    public List<String> getUnlockRequirementsDisplay(FarmType farmType) {
        List<String> display = new ArrayList<>();
        
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        if (requirements != null) {
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                display.add(ChatColor.GRAY + "• " + req.getValue() + "x " + 
                          req.getKey().getDisplayName() + " Tier I");
            }
        }
        
        display.add(ChatColor.GOLD + "• " + plugin.getEconomyManager().formatMoney(farmType.getUnlockCost()));
        
        return display;
    }

    /**
     * Player settings class
     */
    private static class PlayerSettings {
        boolean autoCollect = false;
        boolean hologramsEnabled = true;
        boolean notificationsEnabled = true;
        boolean particleEffectsEnabled = true;
        boolean dropToInventory = false;
    }

    /**
     * Toggle player setting
     */
    public void togglePlayerSetting(UUID uuid, String setting) {
        PlayerSettings settings = playerSettings.computeIfAbsent(uuid, k -> new PlayerSettings());
        
        switch (setting.toLowerCase()) {
            case "autocollect" -> {
                settings.autoCollect = !settings.autoCollect;
                database.savePlayerSetting(uuid, "auto_collect_enabled", settings.autoCollect);
            }
            case "holograms" -> {
                settings.hologramsEnabled = !settings.hologramsEnabled;
                database.savePlayerSetting(uuid, "hologram_enabled", settings.hologramsEnabled);
            }
            case "notifications" -> {
                settings.notificationsEnabled = !settings.notificationsEnabled;
                database.savePlayerSetting(uuid, "notifications_enabled", settings.notificationsEnabled);
            }
            case "particles" -> {
                settings.particleEffectsEnabled = !settings.particleEffectsEnabled;
                database.savePlayerSetting(uuid, "particle_effects_enabled", settings.particleEffectsEnabled);
            }
            case "inventory" -> {
                settings.dropToInventory = !settings.dropToInventory;
                database.savePlayerSetting(uuid, "drop_to_inventory", settings.dropToInventory);
            }
        }
    }
}
