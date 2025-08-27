package org.maks.farmingPlugin.managers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.materials.MaterialType;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles integration with the external IngredientPouch plugin. The manager
 * can query and modify pouch contents as well as coordinate consuming
 * materials for upgrades across a player's inventory and pouch.
 */
public class PouchIntegrationManager {
    private final FarmingPlugin plugin;
    private Object pouchAPI;
    private Method getItemQuantityMethod;
    private Method updateItemQuantityMethod;
    private boolean enabled = false;

    public PouchIntegrationManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    /**
     * Try to hook into IngredientPouchAPI if the plugin is present.
     */
    private void initialize() {
        try {
            Plugin pouchPlugin = plugin.getServer().getPluginManager().getPlugin("IngredientPouchPlugin");
            if (pouchPlugin != null) {
                Method getAPIMethod = pouchPlugin.getClass().getMethod("getAPI");
                Object apiInstance = getAPIMethod.invoke(pouchPlugin);
                if (apiInstance != null) {
                    pouchAPI = apiInstance;
                    Class<?> apiClass = apiInstance.getClass();
                    getItemQuantityMethod = apiClass.getMethod("getItemQuantity", String.class, String.class);
                    updateItemQuantityMethod = apiClass.getMethod("updateItemQuantity", String.class, String.class, int.class);
                    enabled = true;
                    plugin.getLogger().info("✓ Successfully hooked into IngredientPouchPlugin!");
                } else {
                    plugin.getLogger().warning("IngredientPouchPlugin found but API is not available!");
                }
            } else {
                plugin.getLogger().info("IngredientPouchPlugin not found - pouch integration disabled");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize IngredientPouch integration", e);
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Build the pouch item key for a given material type and tier.
     * Format: farmer_[material_id]_[tier_roman]
     */
    private String getPouchItemKey(MaterialType materialType, int tier) {
        String tierRoman = switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };

        return "farmer_" + materialType.getId() + "_" + tierRoman;
    }

    /**
     * Check if player has specific amount of material in their pouch.
     */
    public boolean hasIngredientInPouch(UUID playerUuid, MaterialType materialType, int tier, int amount) {
        if (!enabled || pouchAPI == null) {
            return false;
        }

        try {
            String itemKey = getPouchItemKey(materialType, tier);
            Object result = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
            int currentAmount = result instanceof Integer ? (Integer) result : 0;

            plugin.debug("Checking pouch for " + itemKey + ": has " + currentAmount + ", needs " + amount);

            return currentAmount >= amount;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking pouch ingredient for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Add materials to player's pouch.
     */
    public boolean addIngredientToPouch(UUID playerUuid, MaterialType materialType, int tier, int amount) {
        if (!enabled || pouchAPI == null) {
            return false;
        }

        try {
            String itemKey = getPouchItemKey(materialType, tier);
            Object result = updateItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey, amount);
            boolean success = result instanceof Boolean && (Boolean) result;

            if (success) {
                plugin.debug("Added " + amount + "x " + itemKey + " to " + playerUuid + "'s pouch");
            } else {
                plugin.debug("Failed to add " + itemKey + " to pouch (may be full or item not registered)");
            }

            return success;
        } catch (Exception e) {
            plugin.getLogger().warning("Error adding to pouch for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove materials from player's pouch.
     */
    public boolean removeIngredientFromPouch(UUID playerUuid, MaterialType materialType, int tier, int amount) {
        if (!enabled || pouchAPI == null) {
            return false;
        }

        try {
            String itemKey = getPouchItemKey(materialType, tier);

            // Check current amount first
            Object current = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
            int currentAmount = current instanceof Integer ? (Integer) current : 0;
            if (currentAmount < amount) {
                plugin.debug("Not enough " + itemKey + " in pouch: has " + currentAmount + ", needs " + amount);
                return false;
            }

            Object result = updateItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey, -amount);
            boolean success = result instanceof Boolean && (Boolean) result;

            if (success) {
                plugin.debug("Removed " + amount + "x " + itemKey + " from " + playerUuid + "'s pouch");
            }

            return success;
        } catch (Exception e) {
            plugin.getLogger().warning("Error removing from pouch for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if player has enough materials for upgrade (checks both inventory and pouch)
     * @return true if player has all required materials
     */
    public boolean hasUpgradeMaterials(Player player, Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();

        for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();
            int totalFound = 0;

            // Count materials in inventory
            totalFound += plugin.getMaterialManager().countMaterialInInventory(player, materialType, 1);

            // Also check tier 2 and 3 if needed
            if (totalFound < required) {
                totalFound += plugin.getMaterialManager().countMaterialInInventory(player, materialType, 2);
            }
            if (totalFound < required) {
                totalFound += plugin.getMaterialManager().countMaterialInInventory(player, materialType, 3);
            }

            plugin.debug("Found " + totalFound + " " + materialType.getId() + " in inventory, need " + required);

            // If not enough in inventory, check pouch
            if (totalFound < required && enabled) {
                int needed = required - totalFound;

                // Check all tiers in pouch
                for (int tier = 1; tier <= 3; tier++) {
                    if (hasIngredientInPouch(playerUuid, materialType, tier, needed)) {
                        totalFound += needed;
                        break;
                    }

                    // Check partial amount
                    String itemKey = getPouchItemKey(materialType, tier);
                    Object current = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
                    int pouchAmount = current instanceof Integer ? (Integer) current : 0;
                    if (pouchAmount > 0) {
                        totalFound += Math.min(pouchAmount, needed);
                        needed -= Math.min(pouchAmount, needed);
                        if (needed <= 0) break;
                    }
                }
            }

            if (totalFound < required) {
                plugin.debug("Not enough " + materialType.getId() + ": has " + totalFound + ", needs " + required);
                return false;
            }
        }

        return true;
    }

    /**
     * Consume materials for upgrade from both inventory and pouch
     * @return true if materials were successfully consumed
     */
    public boolean consumeUpgradeMaterials(Player player, Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();

        // First verify we have everything
        if (!hasUpgradeMaterials(player, materials)) {
            return false;
        }

        // Consume materials
        for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();

            // Try to consume from inventory first (prefer lower tiers)
            for (int tier = 1; tier <= 3 && required > 0; tier++) {
                int removed = plugin.getMaterialManager().removeMaterialFromInventory(
                        player, materialType, tier, required);
                required -= removed;

                if (removed > 0) {
                    plugin.debug("Removed " + removed + "x " + materialType.getId() + " tier " + tier + " from inventory");
                }
            }

            // Consume remaining from pouch if needed
            if (required > 0 && enabled) {
                for (int tier = 1; tier <= 3 && required > 0; tier++) {
                    String itemKey = getPouchItemKey(materialType, tier);
                    Object current = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
                    int pouchAmount = current instanceof Integer ? (Integer) current : 0;

                    if (pouchAmount > 0) {
                        int toRemove = Math.min(pouchAmount, required);
                        if (removeIngredientFromPouch(playerUuid, materialType, tier, toRemove)) {
                            required -= toRemove;
                            plugin.debug("Removed " + toRemove + "x " + materialType.getId() + " tier " + tier + " from pouch");
                        }
                    }
                }
            }

            if (required > 0) {
                plugin.getLogger().warning("Failed to consume all required " + materialType.getId() + " for " + player.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Transfer items from inventory to pouch
     */
    public boolean transferToPouch(Player player, MaterialType materialType, int tier, int amount) {
        if (!enabled) {
            player.sendMessage("§cIngredientPouch plugin is not available!");
            return false;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player has the items in inventory
        int inventoryAmount = plugin.getMaterialManager().countMaterialInInventory(player, materialType, tier);
        if (inventoryAmount < amount) {
            player.sendMessage("§cYou don't have enough items to transfer!");
            return false;
        }

        // Remove from inventory
        int removed = plugin.getMaterialManager().removeMaterialFromInventory(player, materialType, tier, amount);
        if (removed != amount) {
            player.sendMessage("§cFailed to remove items from inventory!");
            return false;
        }

        // Add to pouch
        if (addIngredientToPouch(playerUuid, materialType, tier, removed)) {
            player.sendMessage("§aTransferred " + removed + "x " + materialType.getDisplayName() + " Tier " + tier + " to pouch!");
            return true;
        } else {
            // Failed to add to pouch, give items back
            plugin.getMaterialManager().givePlayerMaterial(player, materialType, tier, removed);
            player.sendMessage("§cFailed to add items to pouch (may be full or not registered)!");
            return false;
        }
    }

    /**
     * Get total amount of a material type across all tiers in pouch
     */
    public int getTotalInPouch(UUID playerUuid, MaterialType materialType) {
        if (!enabled || pouchAPI == null) {
            return 0;
        }

        int total = 0;
        for (int tier = 1; tier <= 3; tier++) {
            String itemKey = getPouchItemKey(materialType, tier);
            try {
                Object current = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
                total += current instanceof Integer ? (Integer) current : 0;
            } catch (Exception ignored) {
                // ignore and continue
            }
        }

        return total;
    }

    /**
     * Force reload the integration (useful after IngredientPouch plugin is loaded)
     */
    public void reload() {
        enabled = false;
        pouchAPI = null;
        initialize();
    }
}

