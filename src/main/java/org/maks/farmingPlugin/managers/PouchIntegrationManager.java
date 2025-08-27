package org.maks.farmingPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.materials.MaterialType;
import com.maks.ingredientpouchplugin.IngredientPouchPlugin;
import com.maks.ingredientpouchplugin.api.PouchAPI;

import java.util.UUID;

/**
 * Handles integration with IngredientPouchPlugin to check and consume materials from pouches
 */
public class PouchIntegrationManager {
    
    private final FarmingPlugin plugin;
    private IngredientPouchPlugin pouchPlugin;
    private PouchAPI pouchAPI;
    private boolean enabled;
    
    public PouchIntegrationManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
        
        // Try to hook into IngredientPouchPlugin
        Plugin pouchPluginInstance = Bukkit.getPluginManager().getPlugin("IngredientPouchPlugin");
        if (pouchPluginInstance instanceof IngredientPouchPlugin) {
            this.pouchPlugin = (IngredientPouchPlugin) pouchPluginInstance;
            this.pouchAPI = pouchPlugin.getAPI();
            this.enabled = true;
            plugin.getLogger().info("Successfully hooked into IngredientPouchPlugin!");
        } else {
            plugin.getLogger().info("IngredientPouchPlugin not found, pouch integration disabled.");
        }
    }
    
    /**
     * Check if pouch integration is available
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the pouch item key for a farming material
     * Maps farming plugin materials to pouch plugin item IDs
     */
    private String getPouchItemKey(String materialId, int tier) {
        // Map farming materials to pouch item IDs
        // This might need adjustment based on actual pouch items
        return switch (materialId) {
            case "plant_fiber" -> "plant_fiber";  // Direct mapping
            case "seed_pouch" -> "seed_pouch";    // Direct mapping  
            case "compost_dust" -> "compost_dust"; // Direct mapping
            case "herb_extract" -> "herb_extract"; // Direct mapping
            case "mushroom_spores" -> "mushroom_spores"; // Direct mapping
            case "beeswax_chunk" -> "beeswax_chunk"; // Direct mapping
            case "druidic_essence" -> "druidic_essence"; // Direct mapping
            case "golden_truffle" -> "golden_truffle"; // Direct mapping
            case "ancient_grain" -> "ancient_grain"; // Direct mapping
            default -> materialId; // Fallback to original ID
        };
    }

    /**
     * Check if player has enough of a material in their pouch
     */
    public boolean hasIngredientInPouch(UUID playerUuid, String materialId, int tier, int amount) {
        if (!enabled) return false;
        
        try {
            String ingredientKey = getPouchItemKey(materialId, tier);
            int available = pouchAPI.getItemQuantity(playerUuid.toString(), ingredientKey);
            return available >= amount;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking pouch ingredient: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove materials from player's pouch
     */
    public boolean removeIngredientFromPouch(UUID playerUuid, String materialId, int tier, int amount) {
        if (!enabled) return false;
        
        try {
            String ingredientKey = getPouchItemKey(materialId, tier);
            return pouchAPI.updateItemQuantity(playerUuid.toString(), ingredientKey, -amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Error removing pouch ingredient: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if player has enough materials for upgrade (checks both inventory and pouch)
     */
    public boolean hasUpgradeMaterials(Player player, java.util.Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();
        
        for (java.util.Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();
            
            // Check inventory first
            int inInventory = plugin.getDatabaseManager()
                .getPlayerMaterialAmount(playerUuid, materialType.getId(), 1);
            
            if (inInventory >= required) {
                continue; // Has enough in inventory
            }
            
            // Check pouch for remaining amount needed
            int needed = required - inInventory;
            if (!hasIngredientInPouch(playerUuid, materialType.getId(), 1, needed)) {
                return false; // Not enough in total
            }
        }
        
        return true;
    }
    
    /**
     * Consume materials for upgrade from both inventory and pouch
     */
    public boolean consumeUpgradeMaterials(Player player, java.util.Map<MaterialType, Integer> materials) {
        UUID playerUuid = player.getUniqueId();
        
        // First pass: verify we have everything needed
        if (!hasUpgradeMaterials(player, materials)) {
            return false;
        }
        
        // Second pass: consume materials
        for (java.util.Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
            MaterialType materialType = entry.getKey();
            int required = entry.getValue();
            
            // Try to consume from inventory first
            int inInventory = plugin.getDatabaseManager()
                .getPlayerMaterialAmount(playerUuid, materialType.getId(), 1);
            
            if (inInventory > 0) {
                int toRemoveFromInventory = Math.min(inInventory, required);
                plugin.getDatabaseManager().updatePlayerMaterial(
                    playerUuid, materialType.getId(), 1, -toRemoveFromInventory);
                required -= toRemoveFromInventory;
            }
            
            // Consume remaining from pouch if needed
            if (required > 0) {
                if (!removeIngredientFromPouch(playerUuid, materialType.getId(), 1, required)) {
                    plugin.getLogger().warning("Failed to remove " + required + " " + 
                        materialType.getId() + " from pouch for " + player.getName());
                    return false;
                }
            }
        }
        
        return true;
    }
}