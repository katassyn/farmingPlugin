package org.maks.farmingPlugin.fruits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.maks.farmingPlugin.FarmingPlugin;

import java.util.ArrayList;
import java.util.List;

public enum FruitType {
    SWEET_BERRIES("sweet_berries", Material.SWEET_BERRIES),
    GOLDEN_MELON("golden_melon", Material.GLISTERING_MELON_SLICE),
    MYSTIC_MUSHROOM("mystic_mushroom", Material.RED_MUSHROOM),
    ENCHANTED_PUMPKIN("enchanted_pumpkin", Material.PUMPKIN_PIE),
    ETHEREAL_FLOWER("ethereal_flower", Material.PINK_TULIP),
    ANCIENT_SEED("ancient_seed", Material.MANGROVE_PROPAGULE),
    DESERT_DATE("desert_date", Material.GOLDEN_APPLE);

    private final String id;
    private final Material material;
    private String displayName;
    private long sellPrice;
    private static boolean initialized = false;

    FruitType(String id, Material material) {
        this.id = id;
        this.material = material;
        // Default values - will be overridden by config
        this.displayName = id.replace("_", " ");
        this.sellPrice = 50000L;
    }

    /**
     * Initialize fruit values from config
     */
    public static void initialize(FarmingPlugin plugin) {
        if (initialized) return;

        ConfigurationSection fruitsConfig = plugin.getConfig().getConfigurationSection("fruits");
        if (fruitsConfig == null) {
            plugin.getLogger().warning("No fruits configuration found! Using defaults.");
            return;
        }

        for (FruitType fruit : values()) {
            ConfigurationSection fruitSection = fruitsConfig.getConfigurationSection(fruit.id);
            if (fruitSection != null) {
                fruit.displayName = ChatColor.translateAlternateColorCodes('&',
                    fruitSection.getString("display_name", fruit.displayName));
                fruit.sellPrice = fruitSection.getLong("sell_price", fruit.sellPrice);
            } else {
                plugin.getLogger().warning("No config for fruit: " + fruit.id);
            }
        }

        initialized = true;
        plugin.getLogger().info("Loaded " + values().length + " fruit types from config");
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

    public long getSellPrice() {
        return sellPrice;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(getDisplayName());

            List<String> loreList = new ArrayList<>();
            loreList.add(ChatColor.GRAY + "Fresh farming produce!");
            loreList.add("");
            loreList.add(ChatColor.YELLOW + "⚡ Quick Sell Value:");
            loreList.add(ChatColor.GOLD + "$" + String.format("%,d", sellPrice) + " each");
            loreList.add("");
            loreList.add(ChatColor.DARK_GRAY + "Right-click to eat!");

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

