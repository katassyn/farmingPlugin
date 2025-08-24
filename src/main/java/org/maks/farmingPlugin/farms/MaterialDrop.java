package org.maks.farmingPlugin.farms;

import org.maks.farmingPlugin.materials.MaterialType;

public class MaterialDrop {
    private final MaterialType materialType;
    private final int tier;
    private final double rate;

    public MaterialDrop(MaterialType materialType, int tier, double rate) {
        this.materialType = materialType;
        this.tier = tier;
        this.rate = rate;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public int getTier() {
        return tier;
    }

    public double getRate() {
        return rate;
    }
}