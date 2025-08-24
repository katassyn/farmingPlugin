package org.maks.farmingPlugin.farms;

import org.bukkit.Location;
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

    public FarmInstance(UUID ownerId, FarmType farmType, int instanceId) {
        this.ownerId = ownerId;
        this.farmType = farmType;
        this.instanceId = instanceId;
        this.level = 1;
        this.efficiency = 1;
        this.lastHarvest = System.currentTimeMillis();
        this.storedMaterials = new HashMap<>();
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
    }

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
        this.level = level;
    }

    public int getEfficiency() {
        return efficiency;
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

    public boolean canStoreMore() {
        return getTotalStoredItems() < farmType.getStorageLimit();
    }

    public long getNextHarvestTime() {
        long adjustedGrowthTime = farmType.getGrowthTimeMillis() / efficiency;
        return lastHarvest + adjustedGrowthTime;
    }

    public boolean isReadyForHarvest() {
        return System.currentTimeMillis() >= getNextHarvestTime();
    }

    public long getTimeUntilNextHarvest() {
        long nextHarvest = getNextHarvestTime();
        long now = System.currentTimeMillis();
        return Math.max(0, nextHarvest - now);
    }
}