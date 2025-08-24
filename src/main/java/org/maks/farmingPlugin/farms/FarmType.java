package org.maks.farmingPlugin.farms;

import org.bukkit.Material;
import org.maks.farmingPlugin.materials.MaterialType;

import java.util.Map;

public enum FarmType {
    BERRY_ORCHARDS("berry_orchards", "Berry Orchards", Material.SWEET_BERRY_BUSH, 0, 6, 8, 500),
    MELON_GROVES("melon_groves", "Melon Groves", Material.MELON, 250000000L, 6, 10, 400),
    FUNGAL_CAVERNS("fungal_caverns", "Fungal Caverns", Material.RED_MUSHROOM_BLOCK, 500000000L, 6, 14, 350),
    PUMPKIN_PATCHES("pumpkin_patches", "Pumpkin Patches", Material.PUMPKIN, 750000000L, 6, 16, 300),
    MYSTIC_GARDENS("mystic_gardens", "Mystic Gardens", Material.BEETROOTS, 1500000000L, 3, 20, 200),
    ANCIENT_MANGROVES("ancient_mangroves", "Ancient Mangroves", Material.MANGROVE_PROPAGULE, 4000000000L, 3, 30, 100),
    DESERT_SANCTUARIES("desert_sanctuaries", "Desert Sanctuaries", Material.CACTUS, 10000000000L, 1, 40, 50);

    private final String id;
    private final String displayName;
    private final Material blockType;
    private final long unlockCost;
    private final int maxInstances;
    private final int growthTimeHours;
    private final int storageLimit;

    FarmType(String id, String displayName, Material blockType, long unlockCost, 
             int maxInstances, int growthTimeHours, int storageLimit) {
        this.id = id;
        this.displayName = displayName;
        this.blockType = blockType;
        this.unlockCost = unlockCost;
        this.maxInstances = maxInstances;
        this.growthTimeHours = growthTimeHours;
        this.storageLimit = storageLimit;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getBlockType() {
        return blockType;
    }

    public long getUnlockCost() {
        return unlockCost;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public int getGrowthTimeHours() {
        return growthTimeHours;
    }

    public long getGrowthTimeMillis() {
        return growthTimeHours * 60L * 60L * 1000L;
    }

    public int getStorageLimit() {
        return storageLimit;
    }

    public static FarmType fromId(String id) {
        for (FarmType type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static FarmType fromBlockType(Material blockType) {
        for (FarmType type : values()) {
            if (type.getBlockType() == blockType) {
                return type;
            }
        }
        return null;
    }
}