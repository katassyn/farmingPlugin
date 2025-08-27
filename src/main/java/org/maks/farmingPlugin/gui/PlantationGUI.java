package org.maks.farmingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.farms.MaterialDrop;
import org.maks.farmingPlugin.fruits.FruitType;
import org.maks.farmingPlugin.materials.MaterialManager;
import org.maks.farmingPlugin.materials.MaterialType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlantationGUI implements InventoryHolder {
    private final FarmingPlugin plugin;
    private final FarmInstance farmInstance;
    private final Player player;
    private final Inventory inventory;

    public PlantationGUI(FarmingPlugin plugin, FarmInstance farmInstance, Player player) {
        this.plugin = plugin;
        this.farmInstance = farmInstance;
        this.player = player;

        String title = ChatColor.translateAlternateColorCodes('&',
            "&6" + farmInstance.getFarmType().getDisplayName() + " - Instance " + farmInstance.getInstanceId());
        this.inventory = Bukkit.createInventory(this, 54, title);

        setupGUI();
    }

    private void setupGUI() {
        fillBorders();
        addFarmDisplay();
        addFarmInfo();
        addStoredMaterials();
        addControlButtons();
    }

    private void fillBorders() {
        ItemStack borderItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 45, borderItem);
        }

        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }
    }

    private void addFarmDisplay() {
        // Show the actual farm block in the center top
        Material displayMaterial = getDisplayMaterial(farmInstance.getFarmType());
        ItemStack farmBlock = new ItemStack(displayMaterial);
        ItemMeta farmMeta = farmBlock.getItemMeta();
        
        if (farmMeta != null) {
            farmMeta.setDisplayName(ChatColor.GOLD + farmInstance.getFarmType().getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Instance #" + ChatColor.WHITE + farmInstance.getInstanceId());
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.YELLOW + farmInstance.getLevel() + "/10");
            lore.add("");
            
            // Show what fruit this farm produces
            FruitType fruitType = FruitType.getForFarm(farmInstance.getFarmType());
            if (fruitType != null) {
                lore.add(ChatColor.GREEN + "Produces: " + fruitType.getDisplayName());
                lore.add(ChatColor.GRAY + "Value: " + ChatColor.GOLD + 
                        plugin.getEconomyManager().formatMoney(fruitType.getSellPrice()) + " each");
            }
            
            farmMeta.setLore(lore);
            farmBlock.setItemMeta(farmMeta);
        }
        
        inventory.setItem(4, farmBlock);
    }

    private void addFarmInfo() {
        List<String> lore = new ArrayList<>();
        
        // Harvest status
        if (farmInstance.isReadyForHarvest()) {
            lore.add(ChatColor.GREEN + "✔ Ready for harvest!");
            lore.add(ChatColor.GRAY + "Right-click the farm to collect!");
        } else {
            long timeLeft = farmInstance.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            lore.add(ChatColor.YELLOW + "⏰ Next harvest in: " + timeString);
            
            // Progress bar
            double progress = farmInstance.getHarvestProgress();
            int filledBars = (int) (progress / 10);
            StringBuilder progressBar = new StringBuilder(ChatColor.GREEN + "[");
            for (int i = 0; i < 10; i++) {
                if (i < filledBars) {
                    progressBar.append("■");
                } else {
                    progressBar.append(ChatColor.GRAY).append("□").append(ChatColor.GREEN);
                }
            }
            progressBar.append(ChatColor.GREEN + "] " + ChatColor.WHITE + String.format("%.1f%%", progress));
            lore.add(progressBar.toString());
        }

        long stored = Math.min(farmInstance.getMaxStorage(),
                (System.currentTimeMillis() - farmInstance.getLastHarvest()) / farmInstance.getAdjustedGrowthTime());
        lore.add(ChatColor.GRAY + "Storage: " + ChatColor.WHITE + stored + "/" + farmInstance.getMaxStorage());

        lore.add("");
        lore.add(ChatColor.GRAY + "Total Harvests: " + ChatColor.WHITE + farmInstance.getTotalHarvests());
        lore.add(ChatColor.GRAY + "Experience: " + ChatColor.AQUA + farmInstance.getExp() + 
                " / " + (farmInstance.getLevel() * 100 + (farmInstance.getLevel() - 1) * 50));
        lore.add(ChatColor.GRAY + "Efficiency: " + ChatColor.GREEN + farmInstance.getEfficiency() + "x");

        ItemStack infoItem = createItem(Material.CLOCK,
                ChatColor.GOLD + "Farm Status", lore);
        inventory.setItem(13, infoItem);
    }

    private void addStoredMaterials() {
        // This section shows recently dropped special materials (for reference)
        ItemStack materialsInfo = createItem(Material.CHEST,
            ChatColor.YELLOW + "Special Drops Info", null);
        ItemMeta meta = materialsInfo.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rare materials can drop");
        lore.add(ChatColor.GRAY + "during harvests!");
        lore.add("");
        lore.add(ChatColor.GOLD + "Possible Drops:");
        
        List<MaterialDrop> drops = plugin.getPlantationManager().getFarmDrops(farmInstance.getFarmType());
        for (MaterialDrop drop : drops) {
            double chance = drop.getRate() * (1.0 + (farmInstance.getLevel() - 1) * 0.2);
            lore.add(ChatColor.GRAY + "• " + drop.getMaterialType().getDisplayName() + 
                    " T" + drop.getTier() + ChatColor.DARK_GRAY + " (" + 
                    String.format("%.1f%%", chance) + ")");
        }
        
        meta.setLore(lore);
        materialsInfo.setItemMeta(meta);
        inventory.setItem(31, materialsInfo);
    }

    private void addControlButtons() {
        // Drop rates
        List<String> dropRatesLore = new ArrayList<>();
        dropRatesLore.add(ChatColor.GRAY + "Click to view all");
        dropRatesLore.add(ChatColor.GRAY + "drop rates for this farm");
        ItemStack dropRatesButton = createItem(Material.KNOWLEDGE_BOOK,
                                             ChatColor.BLUE + "Drop Rates", dropRatesLore);
        inventory.setItem(47, dropRatesButton);

        // Quick Sell
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Open the Quick Sell menu");
        sellLore.add(ChatColor.GRAY + "to sell your fruits!");
        ItemStack sellButton = createItem(Material.EMERALD,
                                        ChatColor.GREEN + "Quick Sell", sellLore);
        inventory.setItem(49, sellButton);

        // Upgrade farm
        List<String> upgradeLore = new ArrayList<>();
        upgradeLore.add(ChatColor.GRAY + "Click to upgrade this farm");
        upgradeLore.add(ChatColor.GRAY + "Current Level: " + ChatColor.YELLOW + farmInstance.getLevel());
        ItemStack upgradeButton = createItem(Material.EXPERIENCE_BOTTLE,
                                           ChatColor.YELLOW + "Upgrade Farm", upgradeLore);
        inventory.setItem(51, upgradeButton);

        // Statistics
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "View detailed statistics");
        statsLore.add(ChatColor.GRAY + "for this farm");
        ItemStack statsButton = createItem(Material.BOOK, ChatColor.AQUA + "Statistics", statsLore);
        inventory.setItem(46, statsButton);

        // Settings
        List<String> settingsLore = new ArrayList<>();
        settingsLore.add(ChatColor.GRAY + "Open player settings");
        ItemStack settingsButton = createItem(Material.COMPARATOR,
                                             ChatColor.LIGHT_PURPLE + "Settings", settingsLore);
        inventory.setItem(52, settingsButton);
        
        // Help/Info
        List<String> helpLore = new ArrayList<>();
        helpLore.add(ChatColor.GRAY + "How farming works:");
        helpLore.add(ChatColor.GRAY + "• Wait for harvest timer");
        helpLore.add(ChatColor.GRAY + "• Right-click farm block");
        helpLore.add(ChatColor.GRAY + "• Collect fruits to sell");
        helpLore.add(ChatColor.GRAY + "• Rare materials drop sometimes");
        ItemStack helpButton = createItem(Material.PAPER,
                                        ChatColor.LIGHT_PURPLE + "Help", helpLore);
        inventory.setItem(53, helpButton);
    }

    public void showDropRates() {
        player.closeInventory();
        
        List<MaterialDrop> drops = plugin.getPlantationManager().getFarmDrops(farmInstance.getFarmType());
        FruitType fruitType = FruitType.getForFarm(farmInstance.getFarmType());
        
        player.sendMessage(ChatColor.GOLD + "=== Drop Rates for " + farmInstance.getFarmType().getDisplayName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Level " + farmInstance.getLevel() + 
                         " | Efficiency: " + farmInstance.getEfficiency() + "x");
        player.sendMessage("");
        
        // Show fruit drops (common)
        player.sendMessage(ChatColor.GREEN + "Common Drops (Every Harvest):");
        if (fruitType != null) {
            int baseFruits = 3;
            int maxFruits = 5;
            double levelBonus = 1.0 + (farmInstance.getLevel() - 1) * 0.2;
            player.sendMessage(ChatColor.GRAY + "• " + fruitType.getDisplayName() + 
                             ChatColor.WHITE + " x" + (int)(baseFruits * levelBonus) + "-" + 
                             (int)(maxFruits * levelBonus) + 
                             ChatColor.GOLD + " ($" + fruitType.getSellPrice() + " each)");
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Rare Material Drops:");
        
        for (MaterialDrop drop : drops) {
            String tierRoman = getTierRoman(drop.getTier());
            double actualRate = drop.getRate() * (1.0 + (farmInstance.getLevel() - 1) * 0.2);
            
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + 
                             drop.getMaterialType().getDisplayName() + " " + tierRoman + 
                             ChatColor.GRAY + ": " + ChatColor.YELLOW + String.format("%.1f%%", actualRate) + 
                             ChatColor.DARK_GRAY + " (base: " + drop.getRate() + "%)");
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "Note: Rare materials have a cooldown between drops");
    }
    
    public void showStatistics() {
        player.closeInventory();
        
        Map<String, Object> stats = farmInstance.getStatistics();
        
        player.sendMessage(ChatColor.GOLD + "=== Farm Statistics ===");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.WHITE + farmInstance.getFarmType().getDisplayName());
        player.sendMessage(ChatColor.GRAY + "Instance: " + ChatColor.WHITE + "#" + farmInstance.getInstanceId());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Performance:");
        player.sendMessage(ChatColor.GRAY + "• Total Harvests: " + ChatColor.GREEN + stats.get("totalHarvests"));
        player.sendMessage(ChatColor.GRAY + "• Materials Produced: " + ChatColor.GREEN + stats.get("totalProduced"));
        player.sendMessage(ChatColor.GRAY + "• Avg per Harvest: " + ChatColor.GREEN + 
                         String.format("%.1f", (double) stats.get("avgPerHarvest")));
        player.sendMessage(ChatColor.GRAY + "• Farm Age: " + ChatColor.GREEN + stats.get("ageDays") + " days");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Upgrades:");
        player.sendMessage(ChatColor.GRAY + "• Storage: " + ChatColor.AQUA + "Level " + stats.get("storageUpgrade"));
        player.sendMessage(ChatColor.GRAY + "• Speed: " + ChatColor.AQUA + "Level " + stats.get("speedUpgrade"));
        player.sendMessage(ChatColor.GRAY + "• Quality: " + ChatColor.AQUA + "Level " + stats.get("qualityUpgrade"));
    }
    
    private String getTierRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
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
    
    private Material getDisplayMaterial(FarmType farmType) {
        // Some materials don't work well as GUI items, so we use alternatives
        return switch (farmType) {
            case BERRY_ORCHARDS -> Material.SWEET_BERRIES; // Use berries instead of bush
            case FUNGAL_CAVERNS -> Material.RED_MUSHROOM; // Use mushroom instead of block
            case ANCIENT_MANGROVES -> Material.MANGROVE_LEAVES; // Use leaves instead of propagule
            default -> farmType.getBlockType(); // Use original material for others
        };
    }

    public void refresh() {
        inventory.clear();
        setupGUI();
        
        // Update hologram when GUI is refreshed (storage might have changed)
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(farmInstance, true);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public FarmInstance getFarmInstance() {
        return farmInstance;
    }
}
