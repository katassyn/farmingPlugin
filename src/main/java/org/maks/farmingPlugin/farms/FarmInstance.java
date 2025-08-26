package org.maks.farmingPlugin.farms;

import org.bukkit.Location;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.materials.MaterialType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        return efficiency + speedUpgradeLevel;
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
        return storedMaterials.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get maximum storage capacity with upgrades
     */
    public int getMaxStorage() {
        int baseStorage = farmType.getStorageLimit();
        int bonusStorage = storageUpgradeLevel * 50; // +50 per upgrade level
        double levelBonus = 1.0 + (level - 1) * 0.1; // +10% per farm level
        
        return (int) ((baseStorage + bonusStorage) * levelBonus);
    }

    public boolean canStoreMore() {
        return getTotalStoredItems() < getMaxStorage();
    }

    /**
     * Get adjusted growth time considering all modifiers
     */
    public long getAdjustedGrowthTime() {
        long baseTime = FarmingPlugin.getInstance().getPlantationManager().getHarvestIntervalMillis(this);
        double efficiencyModifier = 1.0 / getEfficiency();
        double levelModifier = 1.0 - (level - 1) * 0.05; // -5% per level

        return (long) (baseTime * efficiencyModifier * levelModifier);
    }

    public long getNextHarvestTime() {
        return lastHarvest + getAdjustedGrowthTime();
    }

    public boolean isReadyForHarvest() {
        return System.currentTimeMillis() >= getNextHarvestTime();
    }

    public long getTimeUntilNextHarvest() {
        long nextHarvest = getNextHarvestTime();
        long now = System.currentTimeMillis();
        return Math.max(0, nextHarvest - now);
    }

    /**
     * Get harvest progress as percentage
     */
    public double getHarvestProgress() {
        long timeSinceHarvest = System.currentTimeMillis() - lastHarvest;
        long growthTime = getAdjustedGrowthTime();
        
        return Math.min(100.0, (timeSinceHarvest * 100.0) / growthTime);
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
        
        double baseCost = 1000000000L; // 1 billion base
        double multiplier = Math.pow(2, currentLevel); // Double each level
        
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
                materials.put(MaterialType.HERBAL_EXTRACT, 10 * nextLevel);
                materials.put(MaterialType.BEESWAX_CHUNK, 5 * nextLevel);
                if (nextLevel > 3) {
                    materials.put(MaterialType.DRUIDIC_ESSENCE, 3 * (nextLevel - 3));
                }
            }
        }
        
        return materials;
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
        stats.put("currentStorage", getTotalStoredItems());
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
        // Full storage
        if (getTotalStoredItems() >= getMaxStorage()) {
            return true;
        }
        
        // Ready to harvest and storage > 75%
        if (isReadyForHarvest() && getTotalStoredItems() > getMaxStorage() * 0.75) {
            return true;
        }
        
        return false;
    }

    /**
     * Get estimated time until storage is full
     */
    public long getTimeUntilStorageFull() {
        if (!canStoreMore()) {
            return 0;
        }
        
        int spaceLeft = getMaxStorage() - getTotalStoredItems();
        double avgPerHarvest = totalHarvests > 0 ? 
            (double) totalMaterialsProduced / totalHarvests : 5.0;
        
        if (avgPerHarvest <= 0) {
            return Long.MAX_VALUE;
        }
        
        int harvestsNeeded = (int) Math.ceil(spaceLeft / avgPerHarvest);
        long growthTime = getAdjustedGrowthTime();
        
        // Account for current harvest progress
        long timeToNextHarvest = getTimeUntilNextHarvest();
        
        return timeToNextHarvest + (harvestsNeeded - 1) * growthTime;
    }

    @Override
    public String toString() {
        return "FarmInstance{" +
               "type=" + farmType.getDisplayName() +
               ", instance=" + instanceId +
               ", level=" + level +
               ", efficiency=" + getEfficiency() +
               ", storage=" + getTotalStoredItems() + "/" + getMaxStorage() +
               ", harvests=" + totalHarvests +
               '}';
    }
}
