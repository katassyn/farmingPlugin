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
import org.maks.farmingPlugin.fruits.FruitType;
import org.maks.farmingPlugin.materials.MaterialManager;
import org.maks.farmingPlugin.materials.MaterialType;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

public class PlantationManager {
    private final FarmingPlugin plugin;
    private final DatabaseManager database;
    private final Gson gson;
    
    private final Map<UUID, List<FarmInstance>> playerFarms;
    private final Map<FarmType, List<MaterialDrop>> farmDrops;
    private final Map<FarmType, Map<MaterialType, Integer>> unlockRequirements;
    
    // Track special material drops separately
    private final Map<UUID, Map<String, Long>> lastSpecialDropTimes;

    public PlantationManager(FarmingPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.gson = new Gson();
        this.playerFarms = new ConcurrentHashMap<>();
        this.farmDrops = new HashMap<>();
        this.unlockRequirements = new HashMap<>();
        this.lastSpecialDropTimes = new ConcurrentHashMap<>();
        
        loadFarmConfigurations();
        startAutoSaveTask();
    }

    private void loadFarmConfigurations() {
        // Configure RARE material drops (special drops)
        // These are now rare drops that happen occasionally
        farmDrops.put(FarmType.BERRY_ORCHARDS, Arrays.asList(
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 15.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 2, 5.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 1, 3.0)
        ));

        farmDrops.put(FarmType.MELON_GROVES, Arrays.asList(
            new MaterialDrop(MaterialType.SEED_POUCH, 1, 12.0),
            new MaterialDrop(MaterialType.SEED_POUCH, 2, 6.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 5.0)
        ));

        farmDrops.put(FarmType.FUNGAL_CAVERNS, Arrays.asList(
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 1, 10.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 2, 5.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 3, 2.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 1, 8.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 2, 3.0)
        ));

        farmDrops.put(FarmType.PUMPKIN_PATCHES, Arrays.asList(
            new MaterialDrop(MaterialType.SEED_POUCH, 2, 8.0),
            new MaterialDrop(MaterialType.SEED_POUCH, 3, 3.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 1, 10.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 2, 5.0),
            new MaterialDrop(MaterialType.COMPOST_DUST, 3, 2.0)
        ));

        farmDrops.put(FarmType.MYSTIC_GARDENS, Arrays.asList(
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 1, 8.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 2, 5.0),
            new MaterialDrop(MaterialType.HERBAL_EXTRACT, 3, 2.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 1, 6.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 2, 3.0)
        ));

        farmDrops.put(FarmType.ANCIENT_MANGROVES, Arrays.asList(
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 1, 5.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 2.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 3, 4.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 3, 3.0)
        ));

        farmDrops.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 1, 3.0),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 2, 1.5),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 3, 0.5),
            new MaterialDrop(MaterialType.ANCIENT_GRAIN, 1, 2.0),
            new MaterialDrop(MaterialType.ANCIENT_GRAIN, 2, 1.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 2.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 3, 1.0)
        ));

        // Load unlock requirements (unchanged)
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
                String sql = "SELECT * FROM farming_player_plantations WHERE uuid = ?";
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
                        
                        int totalHarvests = 0;
                        try {
                            totalHarvests = rs.getInt("total_harvests");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'total_harvests' not found, using default value 0");
                        }
                        
                        int exp = 0;
                        try {
                            exp = rs.getInt("exp");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'exp' not found, using default value 0");
                        }
                        
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
                
                // Initialize special drop timers
                if (!lastSpecialDropTimes.containsKey(playerUuid)) {
                    lastSpecialDropTimes.put(playerUuid, new HashMap<>());
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player plantation data for " + playerUuid, e);
            }
        });
    }

    private Map<String, Integer> loadStoredMaterials(UUID playerUuid, FarmType farmType, int instanceId) {
        try {
            String sql = "SELECT stored_materials_json FROM farming_plantation_storage WHERE uuid = ? AND farm_type = ? AND instance_id = ?";
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
                    String sql = "INSERT INTO farming_player_plantations (uuid, farm_type, instance_id, level, efficiency, last_harvest, total_harvests, exp) " +
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
        
        String sql = "INSERT INTO farming_plantation_storage (uuid, farm_type, instance_id, stored_materials_json, auto_collect) " +
                   "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                   "stored_materials_json = VALUES(stored_materials_json), auto_collect = VALUES(auto_collect)";
        
        PreparedStatement stmt = database.prepareStatement(sql);
        stmt.setString(1, playerUuid.toString());
        stmt.setString(2, farm.getFarmType().getId());
        stmt.setInt(3, farm.getInstanceId());
        stmt.setString(4, json);
        stmt.setBoolean(5, false); // Auto-collect always false now
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
     * Process farm harvest - now drops fruits commonly and materials rarely
     */
    public void processFarmHarvest(FarmInstance farm) {
        if (!farm.isReadyForHarvest()) {
            return;
        }

        Player player = Bukkit.getPlayer(farm.getOwnerId());
        if (player == null) return;

        Random random = new Random();
        Location dropLoc = farm.getLocation().clone().add(0.5, 1.5, 0.5);
        
        // Get fruit type for this farm
        FruitType fruitType = FruitType.getForFarm(farm.getFarmType());
        
        // Calculate fruit drops (common)
        int baseFruitAmount = 3 + random.nextInt(3); // 3-5 base
        double levelMultiplier = 1.0 + (farm.getLevel() - 1) * 0.2;
        int fruitAmount = (int)(baseFruitAmount * levelMultiplier);
        
        // Drop fruits
        if (fruitType != null && fruitAmount > 0) {
            ItemStack fruitItem = fruitType.createItem(fruitAmount);
            player.getWorld().dropItemNaturally(dropLoc, fruitItem);
            
            // Show collection message
            player.sendMessage(ChatColor.GREEN + "✦ Harvested " + fruitAmount + "x " + 
                             fruitType.getDisplayName() + ChatColor.GREEN + "!");
            
            // Particles for fruit harvest
            player.getWorld().spawnParticle(
                org.bukkit.Particle.VILLAGER_HAPPY,
                dropLoc,
                20, 0.5, 0.5, 0.5, 0.1
            );
        }
        
        // Check for special material drops (rare)
        String farmKey = farm.getFarmType().getId() + "_" + farm.getInstanceId();
        Map<String, Long> playerSpecialDrops = lastSpecialDropTimes.get(farm.getOwnerId());
        long lastSpecialDrop = playerSpecialDrops.getOrDefault(farmKey, 0L);
        long currentTime = System.currentTimeMillis();
        
        // Special drops every 5-10 harvests randomly
        boolean shouldDropSpecial = false;
        long timeSinceLastSpecial = currentTime - lastSpecialDrop;
        long specialDropCooldown = 1000L * 60 * 30; // 30 minutes minimum between special drops
        
        if (timeSinceLastSpecial > specialDropCooldown) {
            // 20% chance for special drop after cooldown
            shouldDropSpecial = random.nextDouble() < 0.2;
        }
        
        if (shouldDropSpecial) {
            List<MaterialDrop> drops = farmDrops.get(farm.getFarmType());
            if (drops != null) {
                MaterialManager mm = plugin.getMaterialManager();
                boolean droppedSomething = false;
                
                for (MaterialDrop drop : drops) {
                    double chance = drop.getRate() * levelMultiplier;
                    if (random.nextDouble() * 100.0 <= chance) {
                        ItemStack materialItem = mm.createMaterial(drop.getMaterialType(), drop.getTier(), 1);
                        player.getWorld().dropItemNaturally(dropLoc, materialItem);
                        droppedSomething = true;
                        
                        // Special drop notification
                        player.sendMessage(ChatColor.GOLD + "★ RARE DROP! " + ChatColor.YELLOW + 
                                         drop.getMaterialType().getDisplayName() + " Tier " + drop.getTier());
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        
                        // Special particles
                        player.getWorld().spawnParticle(
                            org.bukkit.Particle.TOTEM,
                            dropLoc,
                            30, 0.5, 1, 0.5, 0.1
                        );
                    }
                }
                
                if (droppedSomething) {
                    playerSpecialDrops.put(farmKey, currentTime);
                }
            }
        }

        // Update harvest time and stats
        farm.setLastHarvest(System.currentTimeMillis());
        farm.incrementHarvests();
        farm.addExp(10 + fruitAmount);
        
        // Check for level up
        checkLevelUp(farm);
        
        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farm);
        }
        
        // Play harvest sound
        player.playSound(dropLoc, Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
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

    public boolean attemptUnlock(Player player, FarmType type, int instanceId) {
        UUID uid = player.getUniqueId();

        Map<MaterialType, Integer> requirements = unlockRequirements.get(type);
        if (requirements != null) {
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                int have = database.getPlayerMaterialAmount(uid, req.getKey().getId(), 1);
                if (have < req.getValue()) {
                    return false;
                }
            }
        }

        if (!plugin.getEconomyManager().hasBalance(uid, type.getUnlockCost())) {
            return false;
        }

        if (requirements != null) {
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                database.updatePlayerMaterial(uid, req.getKey().getId(), 1, -req.getValue());
                removeMaterialFromInventory(player, req.getKey(), 1, req.getValue());
            }
        }

        plugin.getEconomyManager().withdrawMoney(uid, type.getUnlockCost());

        Location loc = plugin.getPlantationAreaManager().getOrCreateFarmAnchor(uid, type, instanceId);
        FarmInstance fi = getFarmInstance(uid, type, instanceId);
        if (fi == null) {
            createFarmInstance(uid, type, instanceId, loc);
        }
        savePlayerData(uid);

        player.sendMessage(ChatColor.GREEN + "Odblokowano: " + type.getDisplayName() + " #" + instanceId);
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

    public long getHarvestIntervalMillis(FarmInstance farm) {
        if (plugin.getConfig().getBoolean("test_mode.enabled", false)) {
            int mins = plugin.getConfig().getInt("test_mode.growth_minutes", 1);
            plugin.getLogger().info("TEST MODE: Farm " + farm.getFarmType().getId() +
                                   " will grow in " + mins + " minute(s)");
            return TimeUnit.MINUTES.toMillis(mins);
        }
        return TimeUnit.SECONDS.toMillis(farm.getFarmType().getHarvestSeconds());
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
}
