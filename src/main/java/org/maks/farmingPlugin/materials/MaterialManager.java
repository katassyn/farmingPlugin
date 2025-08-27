package org.maks.farmingPlugin.materials;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.maks.farmingPlugin.FarmingPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;

public class MaterialManager {
    private final FarmingPlugin plugin;
    private final NamespacedKey materialKey;
    private final NamespacedKey tierKey;

    public MaterialManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.materialKey = new NamespacedKey(plugin, "farmer_material");
        this.tierKey = new NamespacedKey(plugin, "farmer_tier");
    }

    public ItemStack createMaterial(MaterialType type, int tier, int amount) {
        ItemStack item = new ItemStack(type.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            String tierRoman = getTierRoman(tier);
            String tierColor = getTierColor(tier);
            String displayName = ChatColor.translateAlternateColorCodes('&', 
                tierColor + "[ " + tierRoman + " ] " + type.getDisplayName());
            
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', type.getRarity().getLoreDescription()));
            meta.setLore(lore);
            
            meta.addEnchant(Enchantment.DURABILITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.setUnbreakable(true);
            
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, 
                "farmer_" + type.getId() + "_" + tierRoman);
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    public boolean isFarmingMaterial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();

        if (meta.getPersistentDataContainer().has(materialKey, PersistentDataType.STRING)) {
            return true;
        }

        if (!meta.hasDisplayName()) {
            return false;
        }

        String stripped = ChatColor.stripColor(meta.getDisplayName());
        MaterialType type = parseMaterialType(stripped);
        int tier = parseTier(stripped);

        return type != null && tier > 0;
    }

    public MaterialType getMaterialType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        String materialId = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);

        if (materialId != null && materialId.startsWith("farmer_")) {
            String[] parts = materialId.substring(7).split("_");
            if (parts.length >= 2) {
                String typeId = String.join("_", Arrays.copyOf(parts, parts.length - 1));
                return MaterialType.fromId(typeId);
            }
        }

        if (meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            return parseMaterialType(stripped);
        }

        return null;
    }

    public int getMaterialTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER)) {
            return meta.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
        }

        if (meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            return parseTier(stripped);
        }

        return 0;
    }

    private String getTierRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private String getTierColor(int tier) {
        return switch (tier) {
            case 1 -> "&9";
            case 2 -> "&5";
            case 3 -> "&6";
            default -> "&9";
        };
    }

    private MaterialType parseMaterialType(String strippedName) {
        if (strippedName == null) {
            return null;
        }

        int endBracket = strippedName.indexOf(']');
        if (strippedName.startsWith("[") && endBracket > 0) {
            String namePart = strippedName.substring(endBracket + 1).trim();
            for (MaterialType type : MaterialType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(namePart)) {
                    return type;
                }
            }
        }

        return null;
    }

    private int parseTier(String strippedName) {
        if (strippedName == null) {
            return 0;
        }

        if (strippedName.startsWith("[")) {
            int end = strippedName.indexOf(']');
            if (end > 0) {
                String roman = strippedName.substring(1, end).trim().toUpperCase(Locale.ROOT);
                return switch (roman) {
                    case "I" -> 1;
                    case "II" -> 2;
                    case "III" -> 3;
                    default -> 0;
                };
            }
        }

        return 0;
    }
}