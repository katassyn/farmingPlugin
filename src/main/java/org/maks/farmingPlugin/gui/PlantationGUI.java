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
import org.maks.farmingPlugin.farms.MaterialDrop;
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

    private void addFarmInfo() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Farm Type: " + ChatColor.WHITE + farmInstance.getFarmType().getDisplayName());
        lore.add(ChatColor.GRAY + "Instance: " + ChatColor.WHITE + farmInstance.getInstanceId());
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + farmInstance.getLevel());
        lore.add(ChatColor.GRAY + "Efficiency: " + ChatColor.WHITE + farmInstance.getEfficiency());
        lore.add(" ");

        if (farmInstance.isReadyForHarvest()) {
            lore.add(ChatColor.GREEN + "✓ Ready for harvest!");
        } else {
            long timeLeft = farmInstance.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            lore.add(ChatColor.YELLOW + "⏰ Next harvest in: " + timeString);
        }

        lore.add(" ");
        lore.add(ChatColor.GRAY + "Storage: " + ChatColor.WHITE + farmInstance.getTotalStoredItems() +
                 "/" + farmInstance.getFarmType().getStorageLimit());

        ItemStack infoItem = createItem(farmInstance.getFarmType().getBlockType(),
                                      ChatColor.GOLD + "Farm Information", lore);
        inventory.setItem(4, infoItem);
    }

    private void addStoredMaterials() {
        Map<String, Integer> storedMaterials = farmInstance.getStoredMaterials();
        MaterialManager materialManager = plugin.getMaterialManager();

        int slot = 19;
        for (Map.Entry<String, Integer> entry : storedMaterials.entrySet()) {
            if (slot >= 35) break;

            String materialKey = entry.getKey();
            int amount = entry.getValue();

            String[] parts = materialKey.split("_tier_");
            if (parts.length == 2) {
                MaterialType materialType = MaterialType.fromId(parts[0]);
                int tier = Integer.parseInt(parts[1]);

                if (materialType != null) {
                    ItemStack materialItem = materialManager.createMaterial(materialType, tier, amount);
                    ItemMeta meta = materialItem.getItemMeta();

                    if (meta != null) {
                        List<String> lore = new ArrayList<>(meta.getLore());
                        lore.add(" ");
                        lore.add(ChatColor.GRAY + "Stored: " + ChatColor.WHITE + amount);
                        meta.setLore(lore);
                        materialItem.setItemMeta(meta);
                    }

                    inventory.setItem(slot, materialItem);
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
        }
    }

    private void addControlButtons() {
        // Auto-collect toggle
        List<String> autoLore = new ArrayList<>();
        autoLore.add(ChatColor.GRAY + "Toggle auto-collect for this farm");
        autoLore.add(ChatColor.GRAY + "Currently: " + (farmInstance.isAutoCollectEnabled() ?
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        ItemStack autoButton = createItem(Material.HOPPER, ChatColor.YELLOW + "Auto-Collect", autoLore);
        inventory.setItem(45, autoButton);

        // Drop rates
        List<String> dropRatesLore = new ArrayList<>();
        dropRatesLore.add(ChatColor.GRAY + "Click to view drop rates");
        dropRatesLore.add(ChatColor.GRAY + "for this farm type");
        ItemStack dropRatesButton = createItem(Material.KNOWLEDGE_BOOK,
                                             ChatColor.BLUE + "Drop Rates", dropRatesLore);
        inventory.setItem(47, dropRatesButton);

        // Collect all
        List<String> collectLore = new ArrayList<>();
        collectLore.add(ChatColor.GRAY + "Click to drop all materials");
        collectLore.add(ChatColor.GRAY + "on the ground");
        ItemStack collectButton = createItem(Material.HOPPER,
                                           ChatColor.GREEN + "Collect All", collectLore);
        inventory.setItem(49, collectButton);

        // Upgrade farm
        List<String> upgradeLore = new ArrayList<>();
        upgradeLore.add(ChatColor.GRAY + "Click to upgrade this farm");
        ItemStack upgradeButton = createItem(Material.EXPERIENCE_BOTTLE,
                                           ChatColor.YELLOW + "Upgrade Farm", upgradeLore);
        inventory.setItem(51, upgradeButton);

        // Statistics
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "View detailed statistics");
        ItemStack statsButton = createItem(Material.BOOK, ChatColor.AQUA + "Statistics", statsLore);
        inventory.setItem(46, statsButton);

        // Settings
        List<String> settingsLore = new ArrayList<>();
        settingsLore.add(ChatColor.GRAY + "Open player settings");
        ItemStack settingsButton = createItem(Material.COMPARATOR,
                                             ChatColor.LIGHT_PURPLE + "Settings", settingsLore);
        inventory.setItem(52, settingsButton);
    }

    public void collectAllMaterials() {
        MaterialManager materialManager = plugin.getMaterialManager();
        Map<String, Integer> storedMaterials = farmInstance.getStoredMaterials();

        int collected = 0;
        for (Map.Entry<String, Integer> entry : storedMaterials.entrySet()) {
            String materialKey = entry.getKey();
            int amount = entry.getValue();

            String[] parts = materialKey.split("_tier_");
            if (parts.length == 2) {
                MaterialType materialType = MaterialType.fromId(parts[0]);
                int tier = Integer.parseInt(parts[1]);

                if (materialType != null) {
                    ItemStack materialItem = materialManager.createMaterial(materialType, tier, amount);

                    player.getWorld().dropItem(player.getLocation(), materialItem);
                    collected += amount;
                }
            }
        }

        if (collected > 0) {
            farmInstance.clearStoredMaterials();
            plugin.getPlantationManager().savePlayerData(player.getUniqueId());

            player.sendMessage(ChatColor.GREEN + "Dropped " + collected + " materials on the ground!");
            refresh();
        } else {
            player.sendMessage(ChatColor.RED + "No materials to collect!");
        }
    }

    public void showDropRates() {
        player.closeInventory();
        
        List<MaterialDrop> drops = plugin.getPlantationManager().getFarmDrops(farmInstance.getFarmType());
        
        player.sendMessage(ChatColor.GOLD + "=== Drop Rates for " + farmInstance.getFarmType().getDisplayName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Level " + farmInstance.getLevel() + " | Quality Bonus: " + 
                         String.format("%.0f%%", (farmInstance.getQualityModifier() - 1) * 100));
        player.sendMessage("");
        
        for (MaterialDrop drop : drops) {
            String tierRoman = getTierRoman(drop.getTier());
            double actualRate = drop.getRate() * (1.0 + (farmInstance.getLevel() - 1) * 0.2) * farmInstance.getQualityModifier();
            
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + 
                             drop.getMaterialType().getDisplayName() + " " + tierRoman + 
                             ChatColor.GRAY + ": " + ChatColor.GREEN + String.format("%.1f%%", actualRate) + 
                             ChatColor.DARK_GRAY + " (base: " + drop.getRate() + "%)");
        }
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
    
    public void toggleAutoCollect() {
        farmInstance.setAutoCollectEnabled(!farmInstance.isAutoCollectEnabled());
        plugin.getDatabaseManager().savePlayerSetting(player.getUniqueId(), 
                                                     "auto_collect_enabled", 
                                                     farmInstance.isAutoCollectEnabled());
        
        player.sendMessage(ChatColor.YELLOW + "Auto-collect " + 
                         (farmInstance.isAutoCollectEnabled() ? 
                          ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + 
                         ChatColor.YELLOW + " for this farm!");
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

    public void refresh() {
        inventory.clear();
        setupGUI();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public FarmInstance getFarmInstance() {
        return farmInstance;
    }
}
