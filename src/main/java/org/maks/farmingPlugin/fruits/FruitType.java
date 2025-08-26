package org.maks.farmingPlugin.fruits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;

public enum FruitType {
    SWEET_BERRIES("sweet_berries", "&dSweet Berries", Material.SWEET_BERRIES, 50000L,
        new String[]{
            "&7Fresh and juicy berries",
            "&7harvested from magical bushes",
            "",
            "&e⚡ Quick Sell Value: &6$50,000",
            "&8Right-click to eat!"
        }),
    
    GOLDEN_MELON("golden_melon", "&6Golden Melon Slice", Material.GLISTERING_MELON_SLICE, 75000L,
        new String[]{
            "&7A shimmering slice of",
            "&7enchanted melon",
            "",
            "&e⚡ Quick Sell Value: &6$75,000",
            "&8Blessed by the sun"
        }),
    
    MYSTIC_MUSHROOM("mystic_mushroom", "&5Mystic Mushroom", Material.RED_MUSHROOM, 100000L,
        new String[]{
            "&7A rare fungus from",
            "&7the deepest caverns",
            "",
            "&e⚡ Quick Sell Value: &6$100,000",
            "&8Glows in the dark!"
        }),
    
    ENCHANTED_PUMPKIN("enchanted_pumpkin", "&6Enchanted Pumpkin Slice", Material.PUMPKIN_PIE, 125000L,
        new String[]{
            "&7Magical pumpkin infused",
            "&7with autumn essence",
            "",
            "&e⚡ Quick Sell Value: &6$125,000",
            "&8Tastes like fall!"
        }),
    
    ETHEREAL_FLOWER("ethereal_flower", "&dEthereal Blossom", Material.PINK_TULIP, 200000L,
        new String[]{
            "&7A mystical flower that",
            "&7never wilts or fades",
            "",
            "&e⚡ Quick Sell Value: &6$200,000",
            "&8&oWhispers ancient secrets"
        }),
    
    ANCIENT_SEED("ancient_seed", "&2Ancient Mangrove Seed", Material.MANGROVE_PROPAGULE, 350000L,
        new String[]{
            "&7Seeds from the oldest",
            "&7trees in existence",
            "",
            "&e⚡ Quick Sell Value: &6$350,000",
            "&8&oContains primordial power"
        }),
    
    DESERT_DATE("desert_date", "&eDesert Golden Date", Material.GOLDEN_APPLE, 500000L,
        new String[]{
            "&7Legendary fruit from",
            "&7the eternal oasis",
            "",
            "&e⚡ Quick Sell Value: &6$500,000",
            "&8&oBlessed by desert spirits"
        });

    private final String id;
    private final String displayName;
    private final Material material;
    private final long sellPrice;
    private final String[] lore;

    FruitType(String id, String displayName, Material material, long sellPrice, String[] lore) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.sellPrice = sellPrice;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return ChatColor.translateAlternateColorCodes('&', displayName);
    }

    public Material getMaterial() {
        return material;
    }

    public long getSellPrice() {
        return sellPrice;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(getDisplayName());
            
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);
            
            // Add enchantment glow effect
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    public static FruitType fromId(String id) {
        for (FruitType type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get the fruit type for a specific farm
     */
    public static FruitType getForFarm(org.maks.farmingPlugin.farms.FarmType farmType) {
        switch (farmType) {
            case BERRY_ORCHARDS:
                return SWEET_BERRIES;
            case MELON_GROVES:
                return GOLDEN_MELON;
            case FUNGAL_CAVERNS:
                return MYSTIC_MUSHROOM;
            case PUMPKIN_PATCHES:
                return ENCHANTED_PUMPKIN;
            case MYSTIC_GARDENS:
                return ETHEREAL_FLOWER;
            case ANCIENT_MANGROVES:
                return ANCIENT_SEED;
            case DESERT_SANCTUARIES:
                return DESERT_DATE;
            default:
                return null;
        }
    }
}
