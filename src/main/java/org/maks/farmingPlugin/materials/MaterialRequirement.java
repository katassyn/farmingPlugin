package org.maks.farmingPlugin.materials;

public class MaterialRequirement {
    private final MaterialType materialType;
    private final int tier;
    private final int amount;

    public MaterialRequirement(MaterialType materialType, int tier, int amount) {
        this.materialType = materialType;
        this.tier = tier;
        this.amount = amount;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public int getTier() {
        return tier;
    }

    public int getAmount() {
        return amount;
    }

    public String getDisplayName() {
        return materialType.getDisplayName() + " " + getTierRoman(tier);
    }

    private String getTierRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
    }

    /**
     * Check if player has enough of this material (accepting higher tiers)
     */
    public boolean hasEnough(int playerTier1, int playerTier2, int playerTier3) {
        return switch (tier) {
            case 1 -> (playerTier1 + playerTier2 + playerTier3) >= amount;
            case 2 -> (playerTier2 + playerTier3) >= amount;
            case 3 -> playerTier3 >= amount;
            default -> false;
        };
    }

    /**
     * Get total amount available for this requirement (respecting hierarchy)
     */
    public int getTotalAvailable(int playerTier1, int playerTier2, int playerTier3) {
        return switch (tier) {
            case 1 -> playerTier1 + playerTier2 + playerTier3;
            case 2 -> playerTier2 + playerTier3;
            case 3 -> playerTier3;
            default -> 0;
        };
    }
}