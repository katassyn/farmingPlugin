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
        return meta.getPersistentDataContainer().has(materialKey, PersistentDataType.STRING);
    }

    public MaterialType getMaterialType(ItemStack item) {
        if (!isFarmingMaterial(item)) {
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
        
        return null;
    }

    public int getMaterialTier(ItemStack item) {
        if (!isFarmingMaterial(item)) {
            return 0;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
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
}