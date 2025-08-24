package org.maks.farmingPlugin.materials;

import org.bukkit.Material;

public enum MaterialType {
    PLANT_FIBER("plant_fiber", "Plant Fiber", Material.STRING, MaterialRarity.BASIC),
    SEED_POUCH("seed_pouch", "Seed Pouch", Material.WHEAT_SEEDS, MaterialRarity.BASIC),
    COMPOST_DUST("compost_dust", "Compost Dust", Material.BONE_MEAL, MaterialRarity.BASIC),
    
    HERBAL_EXTRACT("herbal_extract", "Herbal Extract", Material.SPIDER_EYE, MaterialRarity.RARE),
    MUSHROOM_SPORES("mushroom_spores", "Mushroom Spores", Material.BROWN_MUSHROOM, MaterialRarity.RARE),
    BEESWAX_CHUNK("beeswax_chunk", "Beeswax Chunk", Material.BOOK, MaterialRarity.RARE),
    
    DRUIDIC_ESSENCE("druidic_essence", "Druidic Essence", Material.GLOW_INK_SAC, MaterialRarity.LEGENDARY),
    GOLDEN_TRUFFLE("golden_truffle", "Golden Truffle", Material.GOLDEN_CARROT, MaterialRarity.LEGENDARY),
    ANCIENT_GRAIN("ancient_grain", "Ancient Grain Sheaf", Material.HAY_BLOCK, MaterialRarity.LEGENDARY);

    private final String id;
    private final String displayName;
    private final Material material;
    private final MaterialRarity rarity;

    MaterialType(String id, String displayName, Material material, MaterialRarity rarity) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.rarity = rarity;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public MaterialRarity getRarity() {
        return rarity;
    }

    public static MaterialType fromId(String id) {
        for (MaterialType type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }
}