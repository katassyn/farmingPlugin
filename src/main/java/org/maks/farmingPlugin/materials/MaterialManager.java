package org.maks.farmingPlugin.materials;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.maks.farmingPlugin.FarmingPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Handles creation and identification of custom farming materials.
 * The class stores both the material type and tier in item metadata and
 * provides several utility methods for working with player inventories.
 */
public class MaterialManager {
    private final FarmingPlugin plugin;
    private final NamespacedKey materialKey;
    private final NamespacedKey tierKey;

    public MaterialManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.materialKey = new NamespacedKey(plugin, "farmer_material");
        this.tierKey = new NamespacedKey(plugin, "farmer_tier");
    }

    /**
     * Create a farming material item stack.
     * Stores a full ID in PersistentDataContainer in format farmer_[id]_TIER
     * to maintain compatibility with IngredientPouch plugin.
     */
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

            // Store full ID compatible with IngredientPouch
            String fullId = "farmer_" + type.getId() + "_" + tierRoman;
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, fullId);
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Determine whether the provided item is a farming material.
     */
    public boolean isFarmingMaterial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // First check NBT/PDC
        if (meta.getPersistentDataContainer().has(materialKey, PersistentDataType.STRING)) {
            return true;
        }

        // Fallback to display name check
        if (!meta.hasDisplayName()) {
            return false;
        }

        String displayName = meta.getDisplayName();
        String stripped = ChatColor.stripColor(displayName);

        // Check pattern like "[ I ] Material"
        if (stripped.matches("\\[\\s*[IVX]+\\s*\\].*")) {
            MaterialType type = parseMaterialType(stripped);
            int tier = parseTier(stripped);
            return type != null && tier > 0;
        }

        return false;
    }

    /**
     * Get the material type represented by an item.
     */
    public MaterialType getMaterialType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();

        // Check NBT/PDC first
        String materialId = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);

        if (materialId != null) {
            if (materialId.startsWith("farmer_")) {
                // Remove prefix and tier suffix
                String cleanId = materialId.substring(7);
                cleanId = cleanId.replaceAll("_[IVX]+$", "");
                return MaterialType.fromId(cleanId);
            }
            return MaterialType.fromId(materialId);
        }

        // Fallback to display name parsing
        if (meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            return parseMaterialType(stripped);
        }

        return null;
    }

    /**
     * Get the tier of the farming material item.
     */
    public int getMaterialTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();

        // Check NBT/PDC first
        if (meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER)) {
            return meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        }

        // Check material key for tier info
        String materialId = meta.getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        if (materialId != null) {
            if (materialId.endsWith("_III")) return 3;
            if (materialId.endsWith("_II")) return 2;
            if (materialId.endsWith("_I")) return 1;
        }

        // Fallback to display name parsing
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

    /**
     * Try to parse a material type from a stripped display name.
     * Supports some legacy/alias names for compatibility.
     */
    private MaterialType parseMaterialType(String strippedName) {
        if (strippedName == null) {
            return null;
        }

        // Pattern: "[ TIER ] Material Name"
        int endBracket = strippedName.indexOf(']');
        if (strippedName.startsWith("[") && endBracket > 0 && endBracket < strippedName.length() - 1) {
            String namePart = strippedName.substring(endBracket + 1).trim();

            for (MaterialType type : MaterialType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(namePart)) {
                    return type;
                }
                // Legacy/alias names
                if (namePart.equalsIgnoreCase("Herbal Extract") && type == MaterialType.HERB_EXTRACT) {
                    return type;
                }
                if (namePart.equalsIgnoreCase("Ancient Grain Sheaf") && type == MaterialType.ANCIENT_GRAIN) {
                    return type;
                }
            }
        }

        return null;
    }

    /**
     * Parse tier from a stripped display name.
     */
    private int parseTier(String strippedName) {
        if (strippedName == null) {
            return 0;
        }

        if (strippedName.startsWith("[")) {
            int end = strippedName.indexOf(']');
            if (end > 0) {
                String tierPart = strippedName.substring(1, end).trim();
                return switch (tierPart.toUpperCase(Locale.ROOT)) {
                    case "I" -> 1;
                    case "II" -> 2;
                    case "III" -> 3;
                    default -> 0;
                };
            }
        }

        return 0;
    }

    /**
     * Get the full IngredientPouch-compatible item ID
     */
    public String getPouchItemId(MaterialType type, int tier) {
        return "farmer_" + type.getId() + "_" + getTierRoman(tier);
    }

    // ---------------------------------------------------------------------
    // Inventory helper methods used by pouch integration and elsewhere
    // ---------------------------------------------------------------------

    /**
     * Count a specific farming material in the player's inventory.
     */
    public int countMaterialInInventory(Player player, MaterialType materialType, int tier) {
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (isFarmingMaterial(item)) {
                MaterialType itemType = getMaterialType(item);
                int itemTier = getMaterialTier(item);

                if (itemType == materialType && itemTier == tier) {
                    count += item.getAmount();
                }
            }
        }

        return count;
    }

    /**
     * Remove a specific amount of material from the player's inventory.
     *
     * @return amount actually removed
     */
    public int removeMaterialFromInventory(Player player, MaterialType materialType, int tier, int amount) {
        int remaining = amount;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || remaining <= 0) continue;

            if (isFarmingMaterial(item)) {
                MaterialType itemType = getMaterialType(item);
                int itemTier = getMaterialTier(item);

                if (itemType == materialType && itemTier == tier) {
                    int stackSize = item.getAmount();

                    if (stackSize <= remaining) {
                        item.setAmount(0);
                        remaining -= stackSize;
                    } else {
                        item.setAmount(stackSize - remaining);
                        remaining = 0;
                    }
                }
            }
        }

        player.updateInventory();
        return amount - remaining;
    }

    /**
     * Give a farming material to the player, dropping any leftovers if the
     * inventory is full.
     */
    public void givePlayerMaterial(Player player, MaterialType materialType, int tier, int amount) {
        ItemStack material = createMaterial(materialType, tier, amount);

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(material);

        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(ChatColor.YELLOW + "Your inventory was full! Some items were dropped.");
        }
    }

    /**
     * Check if the player has at least a certain number of empty inventory
     * slots.
     */
    public boolean hasInventorySpace(Player player, int slots) {
        int emptySlots = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
                if (emptySlots >= slots) {
                    return true;
                }
            }
        }

        return false;
    }
}

