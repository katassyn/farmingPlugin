package org.maks.farmingPlugin.farms;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.materials.MaterialType;
import org.maks.farmingPlugin.materials.MaterialRequirement;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.*;

public class FarmInstance {
    private final UUID ownerId;
    private final FarmType farmType;
    private final int instanceId;
    private Location location;
    private int level;
    private int efficiency;
    private long lastHarvest;
    private final Map<String, Integer> storedMaterials;

    // Additional stats
    private int totalHarvests;
    private int exp;
    private long totalMaterialsProduced;
    private long createdAt;

    // Upgrade levels
    private int storageUpgradeLevel;
    private int speedUpgradeLevel;
    private int qualityUpgradeLevel;

    // Settings
    private boolean autoCollectEnabled;
    private boolean notificationsEnabled;

    // NAPRAWIONE: Przechowywanie aktualnej liczby itemów w storage
    private int currentStoredItems = 0;
    // Czas ostatniego sprawdzenia storage (do kalkulacji offline growth)
    private long lastStorageCheck;

    public FarmInstance(UUID ownerId, FarmType farmType, int instanceId) {
        this.ownerId = ownerId;
        this.farmType = farmType;
        this.instanceId = instanceId;
        this.level = 1;
        this.efficiency = 1;
        this.lastHarvest = System.currentTimeMillis();
        this.storedMaterials = new HashMap<>();
        this.totalHarvests = 0;
        this.exp = 0;
        this.totalMaterialsProduced = 0;
        this.createdAt = System.currentTimeMillis();
        this.storageUpgradeLevel = 0;
        this.speedUpgradeLevel = 0;
        this.qualityUpgradeLevel = 0;
        this.autoCollectEnabled = false;
        this.notificationsEnabled = true;
        this.currentStoredItems = 0;
        this.lastStorageCheck = System.currentTimeMillis();
    }

    public FarmInstance(UUID ownerId, FarmType farmType, int instanceId, int level,
                        int efficiency, long lastHarvest, Map<String, Integer> storedMaterials) {
        this.ownerId = ownerId;
        this.farmType = farmType;
        this.instanceId = instanceId;
        this.level = level;
        this.efficiency = efficiency;
        this.lastHarvest = lastHarvest;
        this.storedMaterials = storedMaterials != null ? new HashMap<>(storedMaterials) : new HashMap<>();
        this.totalHarvests = 0;
        this.exp = 0;
        this.totalMaterialsProduced = 0;
        this.createdAt = System.currentTimeMillis();
        this.storageUpgradeLevel = 0;
        this.speedUpgradeLevel = 0;
        this.qualityUpgradeLevel = 0;
        this.autoCollectEnabled = false;
        this.notificationsEnabled = true;
        this.currentStoredItems = 0;
        this.lastStorageCheck = lastHarvest;
    }

    // Getters and setters
    public UUID getOwnerId() {
        return ownerId;
    }

    public FarmType getFarmType() {
        return farmType;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(level, 10)); // Cap between 1-10
    }

    public int getEfficiency() {
        return 1 + speedUpgradeLevel; // Base efficiency is always 1, upgrades add to it
    }

    public void setEfficiency(int efficiency) {
        this.efficiency = efficiency;
    }

    public long getLastHarvest() {
        return lastHarvest;
    }

    public void setLastHarvest(long lastHarvest) {
        this.lastHarvest = lastHarvest;
    }

    public long getLastStorageCheck() {
        return lastStorageCheck;
    }

    public void setLastStorageCheck(long lastStorageCheck) {
        this.lastStorageCheck = lastStorageCheck;
    }

    public Map<String, Integer> getStoredMaterials() {
        return new HashMap<>(storedMaterials);
    }

    public void addStoredMaterial(String materialKey, int amount) {
        storedMaterials.merge(materialKey, amount, Integer::sum);
        totalMaterialsProduced += amount;
    }

    public void removeStoredMaterial(String materialKey, int amount) {
        int current = storedMaterials.getOrDefault(materialKey, 0);
        if (current <= amount) {
            storedMaterials.remove(materialKey);
        } else {
            storedMaterials.put(materialKey, current - amount);
        }
    }

    public void clearStoredMaterials() {
        storedMaterials.clear();
    }

    public int getTotalStoredItems() {
        return currentStoredItems;
    }

    /**
     * Get maximum storage capacity with upgrades and pet bonuses
     */
    public int getMaxStorage() {
        int baseStorage = farmType.getStorageLimit();
        int bonusStorage = storageUpgradeLevel * 50; // +50 per upgrade level
        double levelBonus = 1.0 + (level - 1) * 0.1; // +10% per farm level

        // Apply pet storage bonus
        double petStorageBonus = getPetFarmStorageBonus();
        double petStorageMultiplier = 1.0 + (petStorageBonus / 100.0);

        return (int) ((baseStorage + bonusStorage) * levelBonus * petStorageMultiplier);
    }

    public boolean canStoreMore() {
        return currentStoredItems < getMaxStorage();
    }

    /**
     * NAPRAWIONE: Oblicz aktualną liczbę itemów w storage na podstawie czasu
     * Ta metoda kalkuluje ile itemów powinno być w storage na podstawie czasu który upłynął
     */
    public int getCurrentStoredItems() {
        updateStorageItems();
        return currentStoredItems;
    }

    /**
     * NOWA METODA: Aktualizuj storage na podstawie czasu który upłynął
     */
    public void updateStorageItems() {
        long currentTime = System.currentTimeMillis();
        long timeSinceCheck = currentTime - lastStorageCheck;
        long growthTime = getAdjustedGrowthTime();

        // Ile cykli wzrostu minęło od ostatniego sprawdzenia
        long cycles = timeSinceCheck / growthTime;

        if (cycles > 0 && currentStoredItems < getMaxStorage()) {
            // Średnia produkcja na cykl (owoce)
            double levelMultiplier = 1.0 + (level - 1) * 0.2;
            int avgFruitsPerCycle = (int) (4 * levelMultiplier); // Średnio 4 owoce * level modifier

            // Dodaj itemy za każdy cykl, ale nie przekrocz limitu
            int toAdd = (int) (cycles * avgFruitsPerCycle);
            currentStoredItems = Math.min(currentStoredItems + toAdd, getMaxStorage());

            // Zaktualizuj czas ostatniego sprawdzenia
            lastStorageCheck = currentTime;
        }
    }

    /**
     * NOWA METODA: Ustaw storage items bezpośrednio (używane przy harvest)
     */
    public void setCurrentStoredItems(int amount) {
        this.currentStoredItems = Math.max(0, Math.min(amount, getMaxStorage()));
        this.lastStorageCheck = System.currentTimeMillis();
    }

    /**
     * NOWA METODA: Wyczyść storage (gdy gracz zbiera)
     */
    public void clearStorage() {
        this.currentStoredItems = 0;
        this.lastStorageCheck = System.currentTimeMillis();
        this.lastHarvest = System.currentTimeMillis();
    }

    /**
     * NOWA METODA: Dodaj items do storage (używane przy growth)
     */
    public void addToStorage(int amount) {
        this.currentStoredItems = Math.min(currentStoredItems + amount, getMaxStorage());
        this.lastStorageCheck = System.currentTimeMillis();
    }

    /**
     * Get adjusted growth time considering all modifiers including pet bonuses
     */
    public long getAdjustedGrowthTime() {
        long baseTime = FarmingPlugin.getInstance().getPlantationManager().getHarvestIntervalMillis(this);
        double efficiencyModifier = 1.0 / getEfficiency();
        double levelModifier = 1.0 - (level - 1) * 0.05; // -5% per level

        // Apply pet growth speed bonus
        double petSpeedBonus = getPetFarmSpeedBonus();
        double petSpeedModifier = 1.0 - (petSpeedBonus / 100.0); // Reduce time by percentage

        return (long) (baseTime * efficiencyModifier * levelModifier * petSpeedModifier);
    }

    // Pet integration methods
    private double getPetFarmSpeedBonus() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;

        String placeholder = PlaceholderAPI.setPlaceholders(owner, "%petplugin_farm_growth_speed%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getPetFarmStorageBonus() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;

        String placeholder = PlaceholderAPI.setPlaceholders(owner, "%petplugin_farm_storage_bonus%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getPetFarmDoubleHarvestChance() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;

        String placeholder = PlaceholderAPI.setPlaceholders(owner, "%petplugin_farm_double_harvest_chance%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getPetFarmSpecialMaterialChance() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;

        String placeholder = PlaceholderAPI.setPlaceholders(owner, "%petplugin_farm_special_material_chance%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getPetFarmFruitSellMultiplier() {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 1.0;

        String placeholder = PlaceholderAPI.setPlaceholders(owner, "%petplugin_farm_fruit_sell_multiplier%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public long getNextHarvestTime() {
        return lastHarvest + getAdjustedGrowthTime();
    }

    public boolean isReadyForHarvest() {
        // Farma jest gotowa do zbioru gdy storage ma coś do zebrania
        updateStorageItems();
        return currentStoredItems > 0;
    }

    public long getTimeUntilNextHarvest() {
        // Jeśli storage jest pełny, pokazuj 0
        if (currentStoredItems >= getMaxStorage()) {
            return 0;
        }

        // Inaczej pokaż czas do następnego cyklu produkcji
        long nextHarvest = lastStorageCheck + getAdjustedGrowthTime();
        long now = System.currentTimeMillis();
        return Math.max(0, nextHarvest - now);
    }

    /**
     * Get harvest progress as percentage
     */
    public double getHarvestProgress() {
        // Jeśli storage jest pełny, pokaż 100%
        if (currentStoredItems >= getMaxStorage()) {
            return 100.0;
        }

        long timeSinceLastCycle = System.currentTimeMillis() - lastStorageCheck;
        long growthTime = getAdjustedGrowthTime();

        return Math.min(100.0, (timeSinceLastCycle * 100.0) / growthTime);
    }

    // Additional stats methods
    public int getTotalHarvests() {
        return totalHarvests;
    }

    public void setTotalHarvests(int totalHarvests) {
        this.totalHarvests = totalHarvests;
    }

    public void incrementHarvests() {
        this.totalHarvests++;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public void addExp(int amount) {
        this.exp += amount;
    }

    public long getTotalMaterialsProduced() {
        return totalMaterialsProduced;
    }

    public void setTotalMaterialsProduced(long total) {
        this.totalMaterialsProduced = total;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    // Upgrade methods
    public int getStorageUpgradeLevel() {
        return storageUpgradeLevel;
    }

    public void setStorageUpgradeLevel(int level) {
        this.storageUpgradeLevel = Math.max(0, Math.min(level, 5)); // Max 5 upgrades
    }

    public int getSpeedUpgradeLevel() {
        return speedUpgradeLevel;
    }

    public void setSpeedUpgradeLevel(int level) {
        this.speedUpgradeLevel = Math.max(0, Math.min(level, 5));
    }

    public int getQualityUpgradeLevel() {
        return qualityUpgradeLevel;
    }

    public void setQualityUpgradeLevel(int level) {
        this.qualityUpgradeLevel = Math.max(0, Math.min(level, 5));
    }

    /**
     * Get quality modifier for drop calculations
     */
    public double getQualityModifier() {
        return 1.0 + qualityUpgradeLevel * 0.15; // +15% drop chance per quality level
    }

    /**
     * Calculate upgrade cost for next level
     */
    public double getUpgradeCost(String upgradeType) {
        int currentLevel = switch (upgradeType.toLowerCase()) {
            case "storage" -> storageUpgradeLevel;
            case "speed" -> speedUpgradeLevel;
            case "quality" -> qualityUpgradeLevel;
            default -> 0;
        };


        // Standard pricing for all farm types
        double baseCost = 10000000L; // 10 million base
        double multiplier = Math.pow(1.5, currentLevel); // 50% increase each level

        return baseCost * multiplier * instanceId; // More expensive for later instances
    }

    /**
     * Get materials required for upgrade
     */
    public Map<MaterialType, Integer> getUpgradeMaterials(String upgradeType, int nextLevel) {
        Map<MaterialType, Integer> materials = new HashMap<>();

        switch (upgradeType.toLowerCase()) {
            case "storage" -> {
                materials.put(MaterialType.PLANT_FIBER, 20 * nextLevel);
                materials.put(MaterialType.SEED_POUCH, 10 * nextLevel);
            }
            case "speed" -> {
                materials.put(MaterialType.COMPOST_DUST, 15 * nextLevel);
                materials.put(MaterialType.MUSHROOM_SPORES, 8 * nextLevel);
            }
            case "quality" -> {
                materials.put(MaterialType.HERB_EXTRACT, 10 * nextLevel);
                materials.put(MaterialType.BEESWAX_CHUNK, 5 * nextLevel);
                if (nextLevel > 3) {
                    materials.put(MaterialType.DRUIDIC_ESSENCE, 3 * (nextLevel - 3));
                }
            }
        }

        return materials;
    }

    /**
     * Get detailed material requirements with specific tiers for upgrade
     */
    public List<MaterialRequirement> getUpgradeMaterialRequirements(String upgradeType, int nextLevel) {
        List<MaterialRequirement> requirements = new ArrayList<>();

        switch (upgradeType.toLowerCase()) {
            case "storage" -> {
                requirements.add(new MaterialRequirement(MaterialType.PLANT_FIBER, 1, 20 * nextLevel));
                requirements.add(new MaterialRequirement(MaterialType.SEED_POUCH, 1, 10 * nextLevel));
            }
            case "speed" -> {
                int compostTier = nextLevel <= 2 ? 1 : 2;
                int sporesTier = nextLevel <= 2 ? 1 : 2;
                requirements.add(new MaterialRequirement(MaterialType.COMPOST_DUST, compostTier, 15 * nextLevel));
                requirements.add(new MaterialRequirement(MaterialType.MUSHROOM_SPORES, sporesTier, 8 * nextLevel));
            }
            case "quality" -> {
                int herbTier = nextLevel <= 2 ? 1 : (nextLevel <= 4 ? 2 : 3);
                int waxTier = nextLevel <= 3 ? 1 : 2;
                requirements.add(new MaterialRequirement(MaterialType.HERB_EXTRACT, herbTier, 10 * nextLevel));
                requirements.add(new MaterialRequirement(MaterialType.BEESWAX_CHUNK, waxTier, 5 * nextLevel));

                if (nextLevel > 3) {
                    requirements.add(new MaterialRequirement(MaterialType.DRUIDIC_ESSENCE, 1, 3 * (nextLevel - 3)));
                }
            }
        }

        return requirements;
    }

    // Settings
    public boolean isAutoCollectEnabled() {
        return autoCollectEnabled;
    }

    public void setAutoCollectEnabled(boolean enabled) {
        this.autoCollectEnabled = enabled;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
    }

    /**
     * Get farm statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("level", level);
        stats.put("efficiency", getEfficiency());
        stats.put("totalHarvests", totalHarvests);
        stats.put("totalProduced", totalMaterialsProduced);
        stats.put("currentStorage", getCurrentStoredItems());
        stats.put("maxStorage", getMaxStorage());
        stats.put("harvestProgress", getHarvestProgress());
        stats.put("exp", exp);
        stats.put("storageUpgrade", storageUpgradeLevel);
        stats.put("speedUpgrade", speedUpgradeLevel);
        stats.put("qualityUpgrade", qualityUpgradeLevel);

        long ageMillis = System.currentTimeMillis() - createdAt;
        long ageDays = ageMillis / (1000L * 60 * 60 * 24);
        stats.put("ageDays", ageDays);

        double avgPerHarvest = totalHarvests > 0 ?
                (double) totalMaterialsProduced / totalHarvests : 0;
        stats.put("avgPerHarvest", avgPerHarvest);

        return stats;
    }

    /**
     * Check if farm needs attention (full storage, ready to harvest, etc.)
     */
    public boolean needsAttention() {
        updateStorageItems();

        // Full storage
        if (currentStoredItems >= getMaxStorage()) {
            return true;
        }

        // Has items to harvest and storage > 75%
        if (currentStoredItems > 0 && currentStoredItems > getMaxStorage() * 0.75) {
            return true;
        }

        return false;
    }

    /**
     * Get estimated time until storage is full
     */
    public long getTimeUntilStorageFull() {
        updateStorageItems();

        if (currentStoredItems >= getMaxStorage()) {
            return 0;
        }

        int spaceLeft = getMaxStorage() - currentStoredItems;
        double levelMultiplier = 1.0 + (level - 1) * 0.2;
        double avgPerCycle = 4 * levelMultiplier;

        if (avgPerCycle <= 0) {
            return Long.MAX_VALUE;
        }

        int cyclesNeeded = (int) Math.ceil(spaceLeft / avgPerCycle);
        long growthTime = getAdjustedGrowthTime();

        // Account for current cycle progress
        long timeToNextCycle = growthTime - (System.currentTimeMillis() - lastStorageCheck);
        if (timeToNextCycle < 0) timeToNextCycle = 0;

        return timeToNextCycle + (cyclesNeeded - 1) * growthTime;
    }

    @Override
    public String toString() {
        return "FarmInstance{" +
                "type=" + farmType.getDisplayName() +
                ", instance=" + instanceId +
                ", level=" + level +
                ", efficiency=" + getEfficiency() +
                ", storage=" + getCurrentStoredItems() + "/" + getMaxStorage() +
                ", harvests=" + totalHarvests +
                '}';
    }
}