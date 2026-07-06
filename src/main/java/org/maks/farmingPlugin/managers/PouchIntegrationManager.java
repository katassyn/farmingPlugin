package org.maks.farmingPlugin.managers;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.materials.MaterialType;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Enhanced integration with IngredientPouch plugin
 * Now properly handles material mapping and provides public methods
 */
public class PouchIntegrationManager {
    private final FarmingPlugin plugin;
    private Object pouchAPI;
    private Method getItemQuantityMethod;
    private Method updateItemQuantityMethod;
    private boolean enabled = false;

    // Correct mapping of material names to pouch keys based on IngredientPouchPlugin
    private static final Map<String, String> MATERIAL_TO_POUCH_KEY = new HashMap<>();
    
    static {
        // These keys match exactly what's in PouchItemMappings.java
        MATERIAL_TO_POUCH_KEY.put("plant_fiber", "farmer_plant_fiber");
        MATERIAL_TO_POUCH_KEY.put("seed_pouch", "farmer_seed_pouch");
        MATERIAL_TO_POUCH_KEY.put("compost_dust", "farmer_compost_dust");
        MATERIAL_TO_POUCH_KEY.put("herb_extract", "farmer_herb_extract");
        MATERIAL_TO_POUCH_KEY.put("mushroom_spores", "farmer_mushroom_spores");
        MATERIAL_TO_POUCH_KEY.put("beeswax_chunk", "farmer_beeswax_chunk");
        MATERIAL_TO_POUCH_KEY.put("druidic_essence", "farmer_druidic_essence");
        MATERIAL_TO_POUCH_KEY.put("golden_truffle", "farmer_golden_truffle");
        MATERIAL_TO_POUCH_KEY.put("ancient_grain", "farmer_ancient_grain");
    }

    public PouchIntegrationManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            Plugin pouchPlugin = plugin.getServer().getPluginManager().getPlugin("IngredientPouchPlugin");
            if (pouchPlugin != null && pouchPlugin.isEnabled()) {
                plugin.getLogger().info("Found IngredientPouchPlugin v" + pouchPlugin.getDescription().getVersion() + ", attempting to hook...");
                
                Method getAPIMethod = pouchPlugin.getClass().getMethod("getAPI");
                Object apiInstance = getAPIMethod.invoke(pouchPlugin);
                
                if (apiInstance != null) {
                    pouchAPI = apiInstance;
                    Class<?> apiClass = apiInstance.getClass();
                    getItemQuantityMethod = apiClass.getMethod("getItemQuantity", String.class, String.class);
                    updateItemQuantityMethod = apiClass.getMethod("updateItemQuantity", String.class, String.class, int.class);
                    enabled = true;
                    plugin.getLogger().info("✓ Successfully hooked into IngredientPouchPlugin API!");
                    
                    // Test the connection
                    testConnection();
                } else {
                    plugin.getLogger().warning("IngredientPouchPlugin found but API is not available!");
                }
            } else {
                plugin.getLogger().info("IngredientPouchPlugin not found or not enabled - pouch integration disabled");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize IngredientPouch integration: " + e.getMessage(), e);
            enabled = false;
        }
    }
    
    /**
     * Test the API connection with a dummy call
     */
    private void testConnection() {
        try {
            // Test with a fake UUID and known item key
            Object result = getItemQuantityMethod.invoke(pouchAPI, "00000000-0000-0000-0000-000000000000", "farmer_plant_fiber_I");
            plugin.getLogger().info("✓ Pouch API connection test successful!");
        } catch (Exception e) {
            plugin.getLogger().warning("Pouch API connection test failed: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the correct pouch item key for a material and tier
     */
    public String getPouchItemKey(MaterialType materialType, int tier) {
        String baseKey = MATERIAL_TO_POUCH_KEY.get(materialType.getId());
        if (baseKey == null) {
            plugin.getLogger().warning("No pouch mapping found for material: " + materialType.getId());
            // Fallback to the standard format
            baseKey = "farmer_" + materialType.getId();
        }
        
        String fullKey = baseKey + "_" + getTierRoman(tier);
        plugin.debug("Material " + materialType.getId() + " tier " + tier + " -> pouch key: " + fullKey);
        return fullKey;
    }
    
    /**
     * Get the API object (for external use if needed)
     */
    public Object getPouchAPI() {
        return pouchAPI;
    }
    
    /**
     * Get the getItemQuantity method (for external use if needed)
     */
    public Method getGetItemQuantityMethod() {
        return getItemQuantityMethod;
    }

    private String getTierRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    /**
     * Get the quantity of a specific material and tier in player's pouch
     */
    public int getItemQuantityInPouch(UUID playerUuid, MaterialType materialType, int tier) {
        if (!enabled || pouchAPI == null) {
            return 0;
        }

        try {
            String itemKey = getPouchItemKey(materialType, tier);
            plugin.debug("Checking pouch quantity for " + playerUuid + ": " + itemKey);
            
            Object result = getItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey);
            int quantity = result instanceof Integer ? (Integer) result : 0;
            
            plugin.debug("Pouch quantity result: " + quantity);
            return quantity;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting pouch quantity for " + materialType.getId() + 
                                     " tier " + tier + " (player " + playerUuid + "): " + e.getMessage());
            return 0;
        }
    }

    /**
     * Update the quantity of a material in player's pouch
     * @param amount positive to add, negative to remove
     */
    public boolean updatePouchQuantity(UUID playerUuid, MaterialType materialType, int tier, int amount) {
        if (!enabled || pouchAPI == null) {
            return false;
        }

        try {
            String itemKey = getPouchItemKey(materialType, tier);
            plugin.debug("Updating pouch quantity for " + playerUuid + ": " + itemKey + " by " + amount);
            
            Object result = updateItemQuantityMethod.invoke(pouchAPI, playerUuid.toString(), itemKey, amount);
            boolean success = result instanceof Boolean && (Boolean) result;
            
            plugin.debug("Pouch update result: " + success);
            return success;
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating pouch quantity for " + materialType.getId() + 
                                     " tier " + tier + " (player " + playerUuid + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if player has enough materials for upgrade (checks both inventory and pouch)
     */
    public boolean hasUpgradeMaterials(Player player, Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();
        
        plugin.debug("=== Checking upgrade materials for " + player.getName() + " ===");
        plugin.debug("Pouch integration enabled: " + enabled);

        for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();
            int totalFound = 0;

            plugin.debug("Need " + required + "x " + materialType.getDisplayName());

            // Count materials in inventory (all tiers)
            for (int tier = 1; tier <= 3; tier++) {
                int invCount = plugin.getMaterialManager().countMaterialInInventory(player, materialType, tier);
                totalFound += invCount;
                if (invCount > 0) {
                    plugin.debug("  Inventory tier " + tier + ": " + invCount);
                }
                
                if (totalFound >= required) break;
            }

            plugin.debug("  Total in inventory: " + totalFound);

            // Check pouch if not enough in inventory
            if (totalFound < required && enabled) {
                int needed = required - totalFound;
                plugin.debug("  Still need " + needed + ", checking pouch...");

                for (int tier = 1; tier <= 3; tier++) {
                    if (needed <= 0) break;
                    
                    int pouchAmount = getItemQuantityInPouch(playerUuid, materialType, tier);
                    if (pouchAmount > 0) {
                        plugin.debug("  Pouch tier " + tier + ": " + pouchAmount);
                        int useFromPouch = Math.min(pouchAmount, needed);
                        totalFound += useFromPouch;
                        needed -= useFromPouch;
                    }
                }
            }

            plugin.debug("  Final total: " + totalFound + "/" + required);

            if (totalFound < required) {
                plugin.debug("❌ Not enough " + materialType.getId() + ": has " + totalFound + ", needs " + required);
                return false;
            }
        }

        plugin.debug("✅ All materials available!");
        return true;
    }

    /**
     * Consume materials from both inventory and pouch for upgrades
     */
    public boolean consumeUpgradeMaterials(Player player, Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();
        
        plugin.debug("=== Consuming upgrade materials for " + player.getName() + " ===");

        // First verify we have everything
        if (!hasUpgradeMaterials(player, materials)) {
            plugin.debug("❌ Material check failed!");
            return false;
        }

        // Consume materials
        for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();
            
            plugin.debug("Consuming " + required + "x " + materialType.getDisplayName());

            // Try to consume from inventory first (prefer lower tiers)
            for (int tier = 1; tier <= 3 && required > 0; tier++) {
                int removed = plugin.getMaterialManager().removeMaterialFromInventory(player, materialType, tier, required);
                required -= removed;
                
                if (removed > 0) {
                    plugin.debug("  Removed " + removed + "x tier " + tier + " from inventory");
                    
                    // Update database
                    plugin.getDatabaseManager().updatePlayerMaterial(
                        playerUuid, materialType.getId(), tier, -removed);
                }
            }

            // Consume remaining from pouch if needed
            if (required > 0 && enabled) {
                plugin.debug("  Still need to remove " + required + " from pouch...");
                
                for (int tier = 1; tier <= 3 && required > 0; tier++) {
                    int pouchAmount = getItemQuantityInPouch(playerUuid, materialType, tier);
                    if (pouchAmount > 0) {
                        int toRemove = Math.min(pouchAmount, required);
                        
                        if (updatePouchQuantity(playerUuid, materialType, tier, -toRemove)) {
                            required -= toRemove;
                            plugin.debug("  Removed " + toRemove + "x tier " + tier + " from pouch");
                        } else {
                            plugin.getLogger().warning("Failed to remove " + toRemove + "x " + 
                                                     materialType.getId() + " tier " + tier + " from pouch");
                        }
                    }
                }
            }

            if (required > 0) {
                plugin.getLogger().warning("❌ Failed to consume all required " + materialType.getId() + 
                                         " for " + player.getName() + " (still need " + required + ")");
                return false;
            }
        }

        plugin.debug("✅ Successfully consumed all materials!");
        return true;
    }

    /**
     * Add materials to player's pouch
     */
    public boolean addIngredientToPouch(UUID playerUuid, MaterialType materialType, int tier, int amount) {
        if (!enabled || pouchAPI == null) {
            return false;
        }

        boolean success = updatePouchQuantity(playerUuid, materialType, tier, amount);

        if (success) {
            plugin.debug("✅ Added " + amount + "x " + materialType.getDisplayName() + " tier " + tier + " to pouch");
        } else {
            plugin.debug("❌ Failed to add " + amount + "x " + materialType.getDisplayName() + " tier " + tier + " to pouch");
        }

        return success;
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
            total += getItemQuantityInPouch(playerUuid, materialType, tier);
        }

        return total;
    }

    /**
     * Force reload the integration (useful after IngredientPouch plugin is loaded)
     */
    public void reload() {
        enabled = false;
        pouchAPI = null;
        getItemQuantityMethod = null;
        updateItemQuantityMethod = null;
        initialize();
    }
}