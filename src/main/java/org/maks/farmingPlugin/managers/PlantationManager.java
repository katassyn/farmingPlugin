package org.maks.farmingPlugin.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.*;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.sql.Connection;
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
    
    // Removed special drop cooldown tracking

    public PlantationManager(FarmingPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.gson = new Gson();
        this.playerFarms = new ConcurrentHashMap<>();
        this.farmDrops = new HashMap<>();
        this.unlockRequirements = new HashMap<>();
        
        loadFarmConfigurations();
        startAutoSaveTask();
    }

    private void loadFarmConfigurations() {
        // Configure RARE material drops (special drops)
        // These are now rare drops that happen occasionally
        farmDrops.put(FarmType.BERRY_ORCHARDS, Arrays.asList(
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 15.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 2, 5.0),
            new MaterialDrop(MaterialType.HERB_EXTRACT, 1, 3.0)
        ));

        farmDrops.put(FarmType.MELON_GROVES, Arrays.asList(
            new MaterialDrop(MaterialType.SEED_POUCH, 1, 12.0),
            new MaterialDrop(MaterialType.SEED_POUCH, 2, 6.0),
            new MaterialDrop(MaterialType.PLANT_FIBER, 1, 5.0),
                new MaterialDrop(MaterialType.PLANT_FIBER, 3, 3.0)
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
            new MaterialDrop(MaterialType.HERB_EXTRACT, 1, 8.0),
            new MaterialDrop(MaterialType.HERB_EXTRACT, 2, 5.0),
            new MaterialDrop(MaterialType.HERB_EXTRACT, 3, 2.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 1, 6.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 2, 3.0)
        ));

        farmDrops.put(FarmType.ANCIENT_MANGROVES, Arrays.asList(
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 1, 5.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 2.0),
            new MaterialDrop(MaterialType.MUSHROOM_SPORES, 3, 4.0),
                new MaterialDrop(MaterialType.ANCIENT_GRAIN, 1, 2.0),
            new MaterialDrop(MaterialType.BEESWAX_CHUNK, 3, 3.0)
        ));

        farmDrops.put(FarmType.DESERT_SANCTUARIES, Arrays.asList(
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 1, 3.0),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 2, 1.5),
            new MaterialDrop(MaterialType.GOLDEN_TRUFFLE, 3, 0.5),
            new MaterialDrop(MaterialType.ANCIENT_GRAIN, 2, 1.0),
                new MaterialDrop(MaterialType.ANCIENT_GRAIN, 3, 0.5),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 2, 2.0),
            new MaterialDrop(MaterialType.DRUIDIC_ESSENCE, 3, 1.0)
        ));

        // Load unlock requirements (unchanged)
        Map<MaterialType, Integer> melonReq = new HashMap<>();
        melonReq.put(MaterialType.PLANT_FIBER, 50);
        melonReq.put(MaterialType.HERB_EXTRACT, 10);
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
        mangroveReq.put(MaterialType.HERB_EXTRACT, 25);
        mangroveReq.put(MaterialType.BEESWAX_CHUNK, 15);
        mangroveReq.put(MaterialType.COMPOST_DUST, 10);
        unlockRequirements.put(FarmType.ANCIENT_MANGROVES, mangroveReq);

        Map<MaterialType, Integer> desertReq = new HashMap<>();
        desertReq.put(MaterialType.DRUIDIC_ESSENCE, 10);
        desertReq.put(MaterialType.BEESWAX_CHUNK, 15);
        desertReq.put(MaterialType.HERB_EXTRACT, 8);
        unlockRequirements.put(FarmType.DESERT_SANCTUARIES, desertReq);
    }

    public void savePlayerData(UUID playerUuid) {
        List<FarmInstance> farms = playerFarms.get(playerUuid);
        if (farms == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (FarmInstance farm : farms) {
                    // NAPRAWIONE: Zaktualizuj storage przed zapisem
                    farm.updateStorageItems();

                    // Save farm data
                    String sql = "INSERT INTO farming_player_plantations (uuid, farm_type, instance_id, level, efficiency, last_harvest, total_harvests, exp, storage_upgrade_level, speed_upgrade_level, quality_upgrade_level, current_stored_items) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                            "level = VALUES(level), efficiency = VALUES(efficiency), last_harvest = VALUES(last_harvest), " +
                            "total_harvests = VALUES(total_harvests), exp = VALUES(exp), storage_upgrade_level = VALUES(storage_upgrade_level), " +
                            "speed_upgrade_level = VALUES(speed_upgrade_level), quality_upgrade_level = VALUES(quality_upgrade_level), current_stored_items = VALUES(current_stored_items)";

                    try (Connection conn = database.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setString(2, farm.getFarmType().getId());
                        stmt.setInt(3, farm.getInstanceId());
                        stmt.setInt(4, farm.getLevel());
                        stmt.setInt(5, farm.getEfficiency());
                        stmt.setLong(6, farm.getLastHarvest());
                        stmt.setInt(7, farm.getTotalHarvests());
                        stmt.setInt(8, farm.getExp());
                        stmt.setInt(9, farm.getStorageUpgradeLevel());
                        stmt.setInt(10, farm.getSpeedUpgradeLevel());
                        stmt.setInt(11, farm.getQualityUpgradeLevel());
                        stmt.setInt(12, farm.getCurrentStoredItems()); // NAPRAWIONE: Zapisz aktualny storage
                        stmt.executeUpdate();
                    }

                    // Save stored materials
                    saveStoredMaterials(playerUuid, farm);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player plantation data for " + playerUuid, e);
            }
        });
    }

    public void loadPlayerData(UUID playerUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Load farms
                String sql = "SELECT * FROM farming_player_plantations WHERE uuid = ?";
                try (Connection conn = database.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    
                    try (ResultSet rs = stmt.executeQuery()) {
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

                        int storageUpgradeLevel = 0;
                        try {
                            storageUpgradeLevel = rs.getInt("storage_upgrade_level");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'storage_upgrade_level' not found, using default value 0");
                        }

                        int speedUpgradeLevel = 0;
                        try {
                            speedUpgradeLevel = rs.getInt("speed_upgrade_level");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'speed_upgrade_level' not found, using default value 0");
                        }

                        int qualityUpgradeLevel = 0;
                        try {
                            qualityUpgradeLevel = rs.getInt("quality_upgrade_level");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'quality_upgrade_level' not found, using default value 0");
                        }

                        int currentStoredItems = 0;
                        try {
                            currentStoredItems = rs.getInt("current_stored_items");
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Column 'current_stored_items' not found, using default value 0");
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
                        instance.setStorageUpgradeLevel(storageUpgradeLevel);
                        instance.setSpeedUpgradeLevel(speedUpgradeLevel);
                        instance.setQualityUpgradeLevel(qualityUpgradeLevel);
                        instance.setCurrentStoredItems(currentStoredItems); // NAPRAWIONE: Wczytaj storage
                        instance.setLastStorageCheck(lastHarvest); // Ustaw czas ostatniego sprawdzenia

                        // Zaktualizuj storage na podstawie czasu offline
                        instance.updateStorageItems();

                        farms.add(instance);

                        // Create hologram if enabled - run on main thread
                        if (plugin.getHologramManager() != null) {
                            final FarmInstance finalInstance = instance;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getHologramManager().updateHologram(finalInstance);
                            });
                        }
                    }
                }

                        playerFarms.put(playerUuid, farms);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player plantation data for " + playerUuid, e);
            }
        });
    }

    private Map<String, Integer> loadStoredMaterials(UUID playerUuid, FarmType farmType, int instanceId) {
        String sql = "SELECT stored_materials_json FROM farming_plantation_storage WHERE uuid = ? AND farm_type = ? AND instance_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, farmType.getId());
            stmt.setInt(3, instanceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("stored_materials_json");
                    if (json != null && !json.isEmpty()) {
                        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                        return gson.fromJson(json, type);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load stored materials", e);
        }
        
        return new HashMap<>();
    }

    private void saveStoredMaterials(UUID playerUuid, FarmInstance farm) throws SQLException {
        String json = gson.toJson(farm.getStoredMaterials());
        
        String sql = "INSERT INTO farming_plantation_storage (uuid, farm_type, instance_id, stored_materials_json, auto_collect) " +
                   "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                   "stored_materials_json = VALUES(stored_materials_json), auto_collect = VALUES(auto_collect)";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, farm.getFarmType().getId());
            stmt.setInt(3, farm.getInstanceId());
            stmt.setString(4, json);
            stmt.setBoolean(5, false); // Auto-collect always false now
            stmt.executeUpdate();
        }
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
     * NAPRAWIONE: Process farm harvest - teraz poprawnie zarządza storage
     * Storage napełnia się automatycznie, gracz zbiera dokładnie tyle ile jest w storage
     * IMPORTANT: This method must only be called from the main thread!
     */
    public void processFarmHarvest(FarmInstance farm) {
        // Ensure execution on main thread
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("processFarmHarvest called from async thread! Scheduling sync execution.");
            Bukkit.getScheduler().runTask(plugin, () -> processFarmHarvest(farm));
            return;
        }

        Player player = Bukkit.getPlayer(farm.getOwnerId());
        if (player == null) return;

        // Aktualizuj storage przed harvest
        farm.updateStorageItems();

        int itemsInStorage = farm.getCurrentStoredItems();

        // Jeśli storage jest pusty, nic nie robimy
        if (itemsInStorage <= 0) {
            player.sendMessage(ChatColor.YELLOW + "⏳ Farm is still growing! Nothing to harvest yet.");
            player.sendMessage(ChatColor.GRAY + "Storage: " + itemsInStorage + "/" + farm.getMaxStorage());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Location dropLoc = getSafeDropLocation(farm);
        FruitType fruitType = FruitType.getForFarm(farm.getFarmType());
        Random random = new Random();

        // Gracz zbiera DOKŁADNIE tyle ile jest w storage
        int fruitsToHarvest = itemsInStorage;

        // Dropuj owoce
        if (fruitType != null && fruitsToHarvest > 0) {
            int totalFruits = fruitsToHarvest;

            // PANDA pet special effect: 25% chance to double harvest at level 75
            double doubleHarvestChance = farm.getPetFarmDoubleHarvestChance();
            if (doubleHarvestChance > 0 && random.nextDouble() * 100 < doubleHarvestChance) {
                totalFruits *= 2;
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ PANDA PET EFFECT: Double harvest!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                // Special particle effect for double harvest
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.HEART,
                        dropLoc,
                        15, 0.8, 0.5, 0.8, 0.1
                );
            }

            ItemStack fruitItem = fruitType.createItem(totalFruits);
            dropItemWithEffect(player.getWorld(), farm.getLocation(), dropLoc, fruitItem, farm.getFarmType());

            player.sendMessage(ChatColor.GREEN + "✦ Harvested " + totalFruits + "x " +
                    fruitType.getDisplayName() + ChatColor.GREEN + " from storage!");

            // Fire FarmHarvestEvent for QuestSystem integration
            try {
                Class<?> listenerClass = Class.forName("org.maks.questsystem.listeners.FarmingListener");
                Class<?> eventClass = null;
                for (Class<?> innerClass : listenerClass.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("FarmHarvestEvent")) {
                        eventClass = innerClass;
                        break;
                    }
                }
                if (eventClass != null) {
                    // Pass actual amount of fruits harvested
                    Object event = eventClass.getConstructor(Player.class, int.class).newInstance(player, totalFruits);
                    Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                }
            } catch (Exception ignored) {
                // QuestSystem might not be loaded
            }

            // Particle effect
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.VILLAGER_HAPPY,
                    dropLoc,
                    20, 0.5, 0.5, 0.5, 0.1
            );
        }

        // RZADKIE DROPY - szansa na materiały podczas zbioru
        // Losujemy dla KAŻDEGO cyklu wzrostu
        List<MaterialDrop> drops = farmDrops.get(farm.getFarmType());

        // Oblicz ile było cykli wzrostu (ile razy farma "urosła")
        double levelMultiplier = 1.0 + (farm.getLevel() - 1) * 0.2;
        double avgFruitsPerCycle = 4 * levelMultiplier;
        int growthCycles = Math.max(1, (int) Math.ceil(fruitsToHarvest / avgFruitsPerCycle));

        if (drops != null && !drops.isEmpty()) {
            double qualityMod = farm.getQualityModifier();
            MaterialManager mm = plugin.getMaterialManager();
            Map<String, Integer> materialCounts = new HashMap<>();

            // Dla KAŻDEGO cyklu wzrostu sprawdzamy special drops
            for (int cycle = 0; cycle < growthCycles; cycle++) {
                for (MaterialDrop drop : drops) {
                    double baseChance = drop.getRate();
                    double finalChance = baseChance * levelMultiplier * qualityMod;

                    // Sprawdź czy material dropuje w tym cyklu
                    double roll = random.nextDouble() * 100.0;

                    if (roll <= finalChance) {
                        // Zlicz materiały (grupujemy przed dropem)
                        String materialKey = drop.getMaterialType().getId() + "_T" + drop.getTier();
                        materialCounts.put(materialKey, materialCounts.getOrDefault(materialKey, 0) + 1);
                    }
                }
            }

            // Teraz dropuj zgrupowane materiały
            for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
                String[] parts = entry.getKey().split("_T");
                String materialId = parts[0];
                int tier = Integer.parseInt(parts[1]);
                int amount = entry.getValue();

                // Znajdź MaterialType
                MaterialType materialType = null;
                for (MaterialDrop drop : drops) {
                    if (drop.getMaterialType().getId().equals(materialId) && drop.getTier() == tier) {
                        materialType = drop.getMaterialType();
                        break;
                    }
                }

                if (materialType != null) {
                    ItemStack materialItem = mm.createMaterial(materialType, tier, amount);

                    if (materialItem != null) {
                        dropItemWithEffect(player.getWorld(), farm.getLocation(), dropLoc, materialItem, farm.getFarmType());

                        player.sendMessage(ChatColor.GOLD + "★ RARE DROP! " + ChatColor.YELLOW +
                                amount + "x " + materialType.getDisplayName() + " Tier " + tier);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

                        // Fire MaterialCollectEvent for QuestSystem integration
                        String tierName = tier == 1 ? "BASIC" : (tier == 2 ? "RARE" : "LEGENDARY");
                        try {
                            Class<?> listenerClass = Class.forName("org.maks.questsystem.listeners.FarmingListener");
                            Class<?> eventClass = null;
                            for (Class<?> innerClass : listenerClass.getDeclaredClasses()) {
                                if (innerClass.getSimpleName().equals("MaterialCollectEvent")) {
                                    eventClass = innerClass;
                                    break;
                                }
                            }
                            if (eventClass != null) {
                                Object event = eventClass.getConstructor(Player.class, String.class)
                                        .newInstance(player, tierName);
                                Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                            }
                        } catch (Exception ignored) {
                            // QuestSystem might not be loaded
                        }

                        // Special particle effect for rare drops
                        player.getWorld().spawnParticle(
                                org.bukkit.Particle.TOTEM,
                                dropLoc,
                                30, 0.5, 1, 0.5, 0.1
                        );
                    }
                }
            }
        }

        // WITHER_SKELETON pet special material drops - sprawdzamy dla każdego cyklu
        double specialMaterialChance = farm.getPetFarmSpecialMaterialChance();
        if (specialMaterialChance > 0) {
            MaterialManager mm = plugin.getMaterialManager();
            MaterialType specialMaterial = getSpecialMaterialForFarm(farm.getFarmType());

            if (specialMaterial != null) {
                Map<Integer, Integer> petDropsByTier = new HashMap<>();

                // Sprawdź dla każdego cyklu wzrostu
                for (int cycle = 0; cycle < growthCycles; cycle++) {
                    if (random.nextDouble() * 100 < specialMaterialChance) {
                        int tier = 1 + random.nextInt(3); // Random tier 1-3
                        petDropsByTier.put(tier, petDropsByTier.getOrDefault(tier, 0) + 1);
                    }
                }

                // Dropuj zgrupowane pet materiały
                for (Map.Entry<Integer, Integer> entry : petDropsByTier.entrySet()) {
                    int tier = entry.getKey();
                    int amount = entry.getValue();

                    ItemStack specialItem = mm.createMaterial(specialMaterial, tier, amount);
                    dropItemWithEffect(player.getWorld(), farm.getLocation(), dropLoc, specialItem, farm.getFarmType());

                    player.sendMessage(ChatColor.DARK_PURPLE + "☠ WITHER SKELETON PET: Special material drop!");
                    player.sendMessage(ChatColor.YELLOW + "★ " + amount + "x " + specialMaterial.getDisplayName() + " Tier " + tier);
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 1.2f);

                    // Special particle effect for wither skeleton drops
                    player.getWorld().spawnParticle(
                            org.bukkit.Particle.SMOKE_LARGE,
                            dropLoc,
                            20, 0.5, 1, 0.5, 0.05
                    );
                }
            }
        }

        // WYCZYŚĆ STORAGE PO ZBIORZE
        farm.clearStorage();

        // Dodaj exp i statystyki
        farm.incrementHarvests();
        farm.addExp(10 + fruitsToHarvest);
        farm.setTotalMaterialsProduced(farm.getTotalMaterialsProduced() + fruitsToHarvest);

        // Sprawdź level up
        checkLevelUp(farm);

        // Pokaż info o storage
        // player.sendMessage(ChatColor.GRAY + "Storage emptied! It will refill over time."); // disabled per request
        // player.sendMessage(ChatColor.GRAY + "Max storage: " + farm.getMaxStorage() + " items"); // disabled per request

        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farm, true);
        }

        // Sound effect
        player.playSound(dropLoc, Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);

        // Save data asynchronously after harvest
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            savePlayerData(farm.getOwnerId());
        });
    }

    /**
     * Check if player can unlock a farm type
     */
    public boolean canUnlockFarm(UUID playerUuid, FarmType farmType) throws InvocationTargetException, IllegalAccessException {
        if (farmType == FarmType.BERRY_ORCHARDS) return true;
        
        // Check if already unlocked
        if (database.isFarmUnlocked(playerUuid, farmType.getId())) {
            return true;
        }
        
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        if (requirements == null) return true;
        
        // Check material requirements (inventory + pouch)
        if (plugin.getPouchIntegrationManager().isEnabled()) {
            java.util.Map<MaterialType, Integer> materialMap = new java.util.HashMap<>();
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                materialMap.put(req.getKey(), req.getValue());
            }
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !plugin.getPouchIntegrationManager().hasUpgradeMaterials(player, materialMap)) {
                return false;
            }
        } else {
            // Fallback to inventory-only check
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                MaterialType materialType = req.getKey();
                int requiredAmount = req.getValue();
                
                // Check tier 1 materials by default
                int playerAmount = database.getPlayerMaterialAmount(playerUuid, materialType.getId(), 1);
                
                if (playerAmount < requiredAmount) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Unlock a farm type for player
     */
    public boolean unlockFarm(UUID playerUuid, FarmType farmType) throws InvocationTargetException, IllegalAccessException {
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
        
        // Consume materials (from inventory and/or pouch)
        Map<MaterialType, Integer> requirements = unlockRequirements.get(farmType);
        Map<String, Integer> usedMaterials = new HashMap<>();
        
        if (requirements != null) {
            if (plugin.getPouchIntegrationManager().isEnabled() && player != null) {
                // Use pouch integration if available
                if (!plugin.getPouchIntegrationManager().consumeUpgradeMaterials(player, requirements)) {
                    if (player != null) {
                        player.sendMessage(ChatColor.RED + "Failed to consume materials for farm unlock!");
                    }
                    return false;
                }
                
                // Record used materials for database
                for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                    usedMaterials.put(req.getKey().getId() + "_tier_1", req.getValue());
                }
            } else {
                // Fallback to inventory-only consumption
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
        
        // Check if player has enough materials (from inventory and pouch)
        if (requirements != null) {
            for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                MaterialType mat = req.getKey();
                int needed = req.getValue();
                
                // Count materials from both inventory and pouch (all tiers)
                int playerAmount = 0;
                
                // Check all tiers in inventory
                for (int tier = 1; tier <= 3; tier++) {
                    playerAmount += plugin.getMaterialManager().countMaterialInInventory(player, mat, tier);
                }
                
                // Add from pouch if enabled
                if (plugin.getPouchIntegrationManager().isEnabled()) {
                    try {
                        for (int tier = 1; tier <= 3; tier++) {
                            playerAmount += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, tier);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Pouch integration error while checking unlock materials: " + e.getMessage());
                        return false;
                    }
                }
                
                if (playerAmount < needed) {
                    return false;
                }
            }
        }

        if (!plugin.getEconomyManager().hasBalance(uid, type.getUnlockCost())) {
            return false;
        }

        // Consume materials
        if (requirements != null) {
            // Use pouch integration if available
            if (plugin.getPouchIntegrationManager().isEnabled()) {
                try {
                    if (!plugin.getPouchIntegrationManager().consumeUpgradeMaterials(player, requirements)) {
                        player.sendMessage(ChatColor.RED + "Failed to consume materials for unlock!");
                        return false;
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error accessing ingredient pouch!");
                    plugin.getLogger().warning("Pouch integration error during unlock: " + e.getMessage());
                    return false;
                }
            } else {
                // Fallback to inventory-only consumption
                for (Map.Entry<MaterialType, Integer> req : requirements.entrySet()) {
                    database.updatePlayerMaterial(uid, req.getKey().getId(), 1, -req.getValue());
                    removeMaterialFromInventory(player, req.getKey(), 1, req.getValue());
                }
            }
        }

        plugin.getEconomyManager().withdrawMoney(uid, type.getUnlockCost());

        Location loc = plugin.getPlantationAreaManager().getOrCreateFarmAnchor(uid, type, instanceId);
        FarmInstance fi = getFarmInstance(uid, type, instanceId);
        if (fi == null) {
            createFarmInstance(uid, type, instanceId, loc);
        }
        savePlayerData(uid);

        player.sendMessage(ChatColor.GREEN + "Unlocked: " + type.getDisplayName() + " #" + instanceId);
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
    
    public void clearAllData() {
        playerFarms.clear();
        // farmDrops and unlockRequirements are config-based, so don't clear them
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
    
    /**
     * Get a safe location to drop items, avoiding cactus blocks that could destroy them
     */
    private Location getSafeDropLocation(FarmInstance farm) {
        Location farmLoc = farm.getLocation();
        
        // For cactus farms, find a safe location away from cactus blocks
        if (farm.getFarmType() == FarmType.DESERT_SANCTUARIES) {
            // Check locations around the farm in expanding radius
            for (int radius = 3; radius <= 8; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // Only check perimeter of current radius
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        
                        Location testLoc = farmLoc.clone().add(dx, 1.5, dz);
                        
                        // Check if this location and surrounding blocks are safe
                        if (isSafeDropLocation(testLoc)) {
                            return testLoc;
                        }
                    }
                }
            }
        }
        
        // Default location for other farms or if no safe location found
        return farmLoc.clone().add(0.5, 1.5, 0.5);
    }
    
    /**
     * Check if a location is safe for dropping items (no cactus nearby)
     */
    private boolean isSafeDropLocation(Location loc) {
        // Check 5x5x3 area around the drop location for better safety
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Location checkLoc = loc.clone().add(dx, dy, dz);
                    org.bukkit.block.Block block = checkLoc.getBlock();
                    
                    // Avoid cactus blocks and other dangerous blocks
                    if (block.getType() == Material.CACTUS || 
                        block.getType() == Material.LAVA ||
                        block.getType() == Material.FIRE ||
                        block.getType() == Material.MAGMA_BLOCK) {
                        return false;
                    }
                }
            }
        }
        
        // Make sure there's a solid block below and air above
        org.bukkit.block.Block below = loc.clone().add(0, -1, 0).getBlock();
        org.bukkit.block.Block above = loc.clone().add(0, 1, 0).getBlock();
        
        return below.getType().isSolid() && (above.getType() == Material.AIR || above.getType() == Material.CAVE_AIR);
    }
    
    /**
     * Get special material type for WITHER_SKELETON pet drops based on farm type
     */
    private MaterialType getSpecialMaterialForFarm(FarmType farmType) {
        return switch (farmType) {
            case BERRY_ORCHARDS -> MaterialType.PLANT_FIBER;
            case MELON_GROVES -> MaterialType.SEED_POUCH;
            case FUNGAL_CAVERNS -> MaterialType.MUSHROOM_SPORES;
            case PUMPKIN_PATCHES -> MaterialType.COMPOST_DUST;
            case MYSTIC_GARDENS -> MaterialType.HERB_EXTRACT;
            case ANCIENT_MANGROVES -> MaterialType.DRUIDIC_ESSENCE;
            case DESERT_SANCTUARIES -> MaterialType.GOLDEN_TRUFFLE;
        };
    }

    /**
     * Drop item with visual effect - spawns at farm location and flies to safe location
     */
    private void dropItemWithEffect(org.bukkit.World world, Location farmLoc, Location safeLoc, ItemStack item, FarmType farmType) {
        // For cactus farms, spawn at cactus and throw to safe location
        if (farmType == FarmType.DESERT_SANCTUARIES) {
            Location spawnLoc = farmLoc.clone().add(0.5, 1.0, 0.5);
            org.bukkit.entity.Item droppedItem = world.dropItem(spawnLoc, item);
            
            // Calculate velocity to throw item to safe location
            org.bukkit.util.Vector direction = safeLoc.toVector().subtract(spawnLoc.toVector()).normalize();
            double distance = spawnLoc.distance(safeLoc);
            
            // Adjust velocity based on distance (stronger throw for further distances)
            double velocityMultiplier = Math.min(0.8, 0.3 + distance * 0.05);
            direction.multiply(velocityMultiplier);
            direction.setY(0.3 + distance * 0.02); // Add upward arc
            
            droppedItem.setVelocity(direction);
            
            // Add visual effect at spawn location
            world.spawnParticle(org.bukkit.Particle.BLOCK_CRACK, spawnLoc, 10, 0.3, 0.3, 0.3, 0.1, Material.CACTUS.createBlockData());
        } else {
            // For other farms, use normal drop behavior
            world.dropItemNaturally(safeLoc, item);
        }
    }
}
