package org.maks.farmingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.materials.MaterialType;
import org.maks.farmingPlugin.materials.MaterialRequirement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FarmUpgradeGUI implements InventoryHolder {
    private final FarmingPlugin plugin;
    private final FarmInstance farmInstance;
    private final Player player;
    private final Inventory inventory;
    
    // Track confirmation states for purchases
    private final Set<String> pendingConfirmations = new HashSet<>();

    public FarmUpgradeGUI(FarmingPlugin plugin, FarmInstance farmInstance, Player player) {
        this.plugin = plugin;
        this.farmInstance = farmInstance;
        this.player = player;
        
        String title = ChatColor.DARK_GREEN + "Upgrade: " + 
                      farmInstance.getFarmType().getDisplayName() + " #" + 
                      farmInstance.getInstanceId();
        this.inventory = Bukkit.createInventory(this, 54, title);
        
        setupGUI();
    }

    private void setupGUI() {
        fillBorders();
        addUpgradeOptions();
        addFarmInfo();
        addBackButton();
    }

    private void fillBorders() {
        ItemStack borderItem = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        
        // Fill top and bottom rows
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 45, borderItem);
        }
        
        // Fill sides
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }
    }

    private void addUpgradeOptions() {
        // Storage Upgrade
        addUpgradeItem(20, Material.CHEST, "&6Storage Upgrade", 
                      farmInstance.getStorageUpgradeLevel(), 5, "storage",
                      "&7Increases storage capacity",
                      "&7Current: &e" + farmInstance.getMaxStorage() + " items",
                      "&7Next: &a+" + 50 + " capacity");

        // Speed Upgrade
        addUpgradeItem(22, Material.SUGAR, "&bSpeed Upgrade", 
                      farmInstance.getSpeedUpgradeLevel(), 5, "speed",
                      "&7Decreases growth time",
                      "&7Current efficiency: &e" + farmInstance.getEfficiency() + "x",
                      "&7Next: &a+1 efficiency");

        // Quality Upgrade - NAPRAWKA: Usuń stare descriptions, nowa metoda sama je generuje
        addUpgradeItem(24, Material.DIAMOND, "&dQuality Upgrade", 
                      farmInstance.getQualityUpgradeLevel(), 5, "quality");

        // Level Up (center bottom)
        addLevelUpItem();
    }

    private void addUpgradeItem(int slot, Material material, String name, 
                                int currentLevel, int maxLevel, String upgradeType,
                                String... description) {
        if (currentLevel >= maxLevel) {
            // Max level reached
            ItemStack maxItem = createItem(material, name + " &c(MAX)", null);
            ItemMeta meta = maxItem.getItemMeta();
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "✔ Maximum level reached!");
            lore.add("");
            for (String desc : description) {
                if (!desc.contains("Next:")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', desc));
                }
            }
            
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            maxItem.setItemMeta(meta);
            
            inventory.setItem(slot, maxItem);
            return;
        }

        // Create upgrade item
        ItemStack item = createItem(material, name + " &7(Lv " + currentLevel + "/" + maxLevel + ")", null);
        ItemMeta meta = item.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        
        // Custom descriptions based on upgrade type
        if (upgradeType.equals("quality")) {
            // NAPRAWKA: Pokazuj rzeczywiste drop rates dla quality upgrade
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Increases drop rates"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Current bonus: &e" + 
                    String.format("%.0f%%", (farmInstance.getQualityModifier() - 1) * 100)));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Next: &a+15% drop multiplier"));
        } else {
            // Dla innych upgrade types użyj oryginalnych descriptions
            for (String desc : description) {
                lore.add(ChatColor.translateAlternateColorCodes('&', desc));
            }
        }
        
        lore.add("");
        lore.add(ChatColor.YELLOW + "Upgrade Cost:");
        
        // Money cost
        double cost = farmInstance.getUpgradeCost(upgradeType);
        lore.add(ChatColor.GOLD + "• " + plugin.getEconomyManager().formatMoney(cost));
        
        // Material requirements with specific tiers
        List<MaterialRequirement> requirements = farmInstance.getUpgradeMaterialRequirements(upgradeType, currentLevel + 1);
        for (MaterialRequirement req : requirements) {
            MaterialType mat = req.getMaterialType();
            int amount = req.getAmount();
            int requiredTier = req.getTier();
            
            // Count materials from both inventory and pouch
            int playerTier1 = 0, playerTier2 = 0, playerTier3 = 0;
            
            // Check all tiers in inventory
            playerTier1 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 1);
            playerTier2 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 2);
            playerTier3 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 3);
            
            // Add from pouch if enabled
            if (plugin.getPouchIntegrationManager().isEnabled()) {
                try {
                    playerTier1 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 1);
                    playerTier2 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 2);
                    playerTier3 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 3);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error accessing pouch for material " + mat.getId());
                }
            }
            
            // Check if player has enough using hierarchy
            boolean hasEnough = req.hasEnough(playerTier1, playerTier2, playerTier3);
            int totalAvailable = req.getTotalAvailable(playerTier1, playerTier2, playerTier3);
            
            ChatColor color = hasEnough ? ChatColor.GREEN : ChatColor.RED;
            lore.add(color + "• " + amount + "x " + req.getDisplayName() + " " +
                    ChatColor.GRAY + "(" + totalAvailable + ")");
        }
        
        lore.add("");
        if (canAffordUpgrade(upgradeType)) {
            lore.add(ChatColor.GREEN + "Click to purchase upgrade!");
        } else {
            lore.add(ChatColor.RED + "Insufficient resources!");
        }
        
        meta.setLore(lore);
        
        // Add glow if affordable
        if (canAffordUpgrade(upgradeType)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    // NAPRAWKA: Przeciążona metoda dla quality upgrade bez description parametrów
    private void addUpgradeItem(int slot, Material material, String name, 
                                int currentLevel, int maxLevel, String upgradeType) {
        addUpgradeItem(slot, material, name, currentLevel, maxLevel, upgradeType, new String[0]);
    }

    private void addLevelUpItem() {
        int currentLevel = farmInstance.getLevel();
        
        if (currentLevel >= 10) {
            ItemStack maxLevel = createItem(Material.NETHER_STAR, 
                                           "&6Farm Level &c(MAX)", null);
            ItemMeta meta = maxLevel.getItemMeta();
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "✔ Maximum level reached!");
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.GOLD + "10");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Benefits:");
            lore.add(ChatColor.GRAY + "• +90% storage capacity");
            lore.add(ChatColor.GRAY + "• +45% growth speed");
            lore.add(ChatColor.GRAY + "• +180% drop rates");
            
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            maxLevel.setItemMeta(meta);
            
            inventory.setItem(40, maxLevel);
            return;
        }

        ItemStack levelItem = createItem(Material.EXPERIENCE_BOTTLE, 
                                        "&6Level Up &7(" + currentLevel + " → " + (currentLevel + 1) + ")", 
                                        null);
        ItemMeta meta = levelItem.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current Level: " + ChatColor.YELLOW + currentLevel);
        lore.add(ChatColor.GRAY + "Experience: " + ChatColor.AQUA + farmInstance.getExp() + 
                "/" + getExpForLevel(currentLevel + 1));
        
        lore.add("");
        lore.add(ChatColor.GREEN + "Level Benefits:");
        lore.add(ChatColor.GRAY + "• +10% storage capacity");
        lore.add(ChatColor.GRAY + "• +5% growth speed");
        lore.add(ChatColor.GRAY + "• +20% drop rate multiplier");
        
        if (farmInstance.getExp() >= getExpForLevel(currentLevel + 1)) {
            lore.add("");
            lore.add(ChatColor.GREEN + "✔ Click to level up!");
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add("");
            lore.add(ChatColor.RED + "Need more experience!");
            lore.add(ChatColor.GRAY + "Gain exp by harvesting");
        }
        
        meta.setLore(lore);
        levelItem.setItemMeta(meta);
        
        inventory.setItem(40, levelItem);
    }

    private void addFarmInfo() {
        ItemStack infoItem = createItem(farmInstance.getFarmType().getBlockType(),
                                       "&e" + farmInstance.getFarmType().getDisplayName() + " Info",
                                       null);
        ItemMeta meta = infoItem.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Instance: " + ChatColor.WHITE + "#" + farmInstance.getInstanceId());
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.YELLOW + farmInstance.getLevel());
        lore.add(ChatColor.GRAY + "Total Harvests: " + ChatColor.GREEN + farmInstance.getTotalHarvests());
        lore.add("");
        lore.add(ChatColor.AQUA + "Current Stats:");
        lore.add(ChatColor.GRAY + "• Storage: " + ChatColor.WHITE + farmInstance.getMaxStorage());
        lore.add(ChatColor.GRAY + "• Efficiency: " + ChatColor.WHITE + farmInstance.getEfficiency() + "x");
        lore.add(ChatColor.GRAY + "• Quality: " + ChatColor.WHITE + 
                String.format("+%.0f%%", (farmInstance.getQualityModifier() - 1) * 100));
        lore.add("");
        lore.add(ChatColor.GOLD + "Total Materials Produced:");
        lore.add(ChatColor.YELLOW + "" + farmInstance.getTotalMaterialsProduced());
        
        meta.setLore(lore);
        infoItem.setItemMeta(meta);
        
        inventory.setItem(4, infoItem);
    }

    private void addBackButton() {
        ItemStack backButton = createItem(Material.ARROW, "&cBack to Farm", null);
        ItemMeta meta = backButton.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Return to farm menu");
        meta.setLore(lore);
        backButton.setItemMeta(meta);
        
        inventory.setItem(49, backButton);
    }

    public void purchaseUpgrade(String upgradeType) {
        int currentLevel = switch (upgradeType.toLowerCase()) {
            case "storage" -> farmInstance.getStorageUpgradeLevel();
            case "speed" -> farmInstance.getSpeedUpgradeLevel();
            case "quality" -> farmInstance.getQualityUpgradeLevel();
            default -> 0;
        };

        if (currentLevel >= 5) {
            player.sendMessage(ChatColor.RED + "This upgrade is already at maximum level!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!canAffordUpgrade(upgradeType)) {
            player.sendMessage(ChatColor.RED + "You cannot afford this upgrade!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if this is the first click (show cost and confirmation prompt)
        if (!pendingConfirmations.contains(upgradeType)) {
            pendingConfirmations.add(upgradeType);
            
            // Show cost breakdown
            player.sendMessage(ChatColor.YELLOW + "══════════════════════════");
            player.sendMessage(ChatColor.GOLD + upgradeType.toUpperCase() + " UPGRADE COST:");
            
            // Money cost
            double cost = farmInstance.getUpgradeCost(upgradeType);
            player.sendMessage(ChatColor.GOLD + "• Money: " + plugin.getEconomyManager().formatMoney(cost));
            
            // Material costs
            Map<MaterialType, Integer> materials = farmInstance.getUpgradeMaterials(upgradeType, currentLevel + 1);
            for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
                player.sendMessage(ChatColor.GOLD + "• " + entry.getValue() + "x " + entry.getKey().getDisplayName());
            }
            
            player.sendMessage(ChatColor.GREEN + "Click again to confirm purchase!");
            player.sendMessage(ChatColor.YELLOW + "══════════════════════════");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return;
        }
        
        // Second click - proceed with purchase
        pendingConfirmations.remove(upgradeType);

        // Deduct costs
        double cost = farmInstance.getUpgradeCost(upgradeType);
        plugin.getEconomyManager().withdrawMoney(player, cost);

        Map<MaterialType, Integer> materials = farmInstance.getUpgradeMaterials(upgradeType, currentLevel + 1);

        // Consume materials (from inventory and/or pouch)
        if (plugin.getPouchIntegrationManager().isEnabled()) {
            try {
                if (!plugin.getPouchIntegrationManager().consumeUpgradeMaterials(player, materials)) {
                    player.sendMessage(ChatColor.RED + "Failed to consume materials for upgrade!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Error accessing ingredient pouch!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                plugin.getLogger().warning("Pouch integration error during upgrade: " + e.getMessage());
                return;
            }
        } else {
            // Fallback to inventory-only consumption
            for (Map.Entry<MaterialType, Integer> entry : materials.entrySet()) {
                plugin.getDatabaseManager().updatePlayerMaterial(
                    player.getUniqueId(),
                    entry.getKey().getId(),
                    1, 
                    -entry.getValue()
                );
            }
        }

        // Apply upgrade
        switch (upgradeType.toLowerCase()) {
            case "storage" -> farmInstance.setStorageUpgradeLevel(currentLevel + 1);
            case "speed" -> farmInstance.setSpeedUpgradeLevel(currentLevel + 1);
            case "quality" -> farmInstance.setQualityUpgradeLevel(currentLevel + 1);
        }

        // Save and refresh
        plugin.getPlantationManager().savePlayerData(player.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "✔ Successfully purchased " + upgradeType + " upgrade!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Fire FarmUpgradeEvent for QuestSystem integration
        try {
            Class<?> listenerClass = Class.forName("org.maks.questsystem.listeners.FarmingListener");
            Class<?> eventClass = null;
            for (Class<?> innerClass : listenerClass.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("FarmUpgradeEvent")) {
                    eventClass = innerClass;
                    break;
                }
            }
            if (eventClass != null) {
                Object event = eventClass.getConstructor(org.bukkit.entity.Player.class)
                        .newInstance(player);
                org.bukkit.Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
            }
        } catch (Exception ignored) {
            // QuestSystem might not be loaded
        }

        // Particle effect
        player.getWorld().spawnParticle(
            org.bukkit.Particle.TOTEM,
            player.getLocation().add(0, 1, 0),
            30, 0.5, 0.5, 0.5, 0.1
        );
        
        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farmInstance);
        }
        
        // Refresh GUI
        setupGUI();
    }

    public void attemptLevelUp() {
        int currentLevel = farmInstance.getLevel();
        
        if (currentLevel >= 10) {
            player.sendMessage(ChatColor.RED + "Farm is already at maximum level!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int expNeeded = getExpForLevel(currentLevel + 1);
        
        if (farmInstance.getExp() < expNeeded) {
            player.sendMessage(ChatColor.RED + "Not enough experience to level up!");
            player.sendMessage(ChatColor.GRAY + "Need: " + expNeeded + " exp, Have: " + farmInstance.getExp());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Level up
        farmInstance.setLevel(currentLevel + 1);
        farmInstance.setExp(farmInstance.getExp() - expNeeded);
        
        player.sendMessage(ChatColor.GOLD + "⬆ LEVEL UP! Farm is now level " + farmInstance.getLevel() + "!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        // Effects
        player.getWorld().spawnParticle(
            org.bukkit.Particle.TOTEM,
            farmInstance.getLocation().clone().add(0.5, 1, 0.5),
            50, 1, 1, 1, 0.1
        );
        
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().showLevelUpAnimation(farmInstance.getLocation(), farmInstance.getLevel());
        }
        
        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farmInstance);
        }
        
        // Save and refresh
        plugin.getPlantationManager().savePlayerData(player.getUniqueId());
        setupGUI();
    }

    private boolean canAffordUpgrade(String upgradeType) {
        double cost = farmInstance.getUpgradeCost(upgradeType);
        
        if (!plugin.getEconomyManager().hasBalance(player, cost)) {
            return false;
        }

        int nextLevel = switch (upgradeType.toLowerCase()) {
            case "storage" -> farmInstance.getStorageUpgradeLevel() + 1;
            case "speed" -> farmInstance.getSpeedUpgradeLevel() + 1;
            case "quality" -> farmInstance.getQualityUpgradeLevel() + 1;
            default -> 1;
        };

        // Check material requirements with tier hierarchy
        List<MaterialRequirement> requirements = farmInstance.getUpgradeMaterialRequirements(upgradeType, nextLevel);
        
        for (MaterialRequirement req : requirements) {
            MaterialType mat = req.getMaterialType();
            
            // Count materials from both inventory and pouch
            int playerTier1 = 0, playerTier2 = 0, playerTier3 = 0;
            
            // Check all tiers in inventory
            playerTier1 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 1);
            playerTier2 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 2);
            playerTier3 += plugin.getMaterialManager().countMaterialInInventory(player, mat, 3);
            
            // Add from pouch if enabled
            if (plugin.getPouchIntegrationManager().isEnabled()) {
                try {
                    playerTier1 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 1);
                    playerTier2 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 2);
                    playerTier3 += plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), mat, 3);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error accessing pouch for material " + mat.getId());
                }
            }
            
            // Check if player has enough using hierarchy
            if (!req.hasEnough(playerTier1, playerTier2, playerTier3)) {
                return false;
            }
        }
        
        return true;
    }

    private int getExpForLevel(int level) {
        return level * 100 + (level - 1) * 50;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public FarmInstance getFarmInstance() {
        return farmInstance;
    }
}
