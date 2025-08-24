package org.maks.farmingPlugin.materials;

public enum MaterialRarity {
    BASIC("&9", "&7&oBasic crafting material"),
    RARE("&a", "&a&oRare crafting material"),
    LEGENDARY("&c", "&c&oLegendary crafting material");

    private final String color;
    private final String loreDescription;

    MaterialRarity(String color, String loreDescription) {
        this.color = color;
        this.loreDescription = loreDescription;
    }

    public String getColor() {
        return color;
    }

    public String getLoreDescription() {
        return loreDescription;
    }
}