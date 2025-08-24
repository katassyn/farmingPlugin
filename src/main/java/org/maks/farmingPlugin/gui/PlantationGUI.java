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
        lore.add("");
        
        if (farmInstance.isReadyForHarvest()) {
            lore.add(ChatColor.GREEN + "✓ Ready for harvest!");
        } else {
            long timeLeft = farmInstance.getTimeUntilNextHarvest();
            String timeString = formatTime(timeLeft);
            lore.add(ChatColor.YELLOW + "⏰ Next harvest in: " + timeString);
        }
        
        lore.add("");
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
                        lore.add("");
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
        List<String> collectLore = new ArrayList<>();
        collectLore.add(ChatColor.GRAY + "Click to collect all materials");
        collectLore.add(ChatColor.GRAY + "to your inventory");
        ItemStack collectButton = createItem(Material.HOPPER, 
                                           ChatColor.GREEN + "Collect All", collectLore);
        inventory.setItem(49, collectButton);
        
        List<String> dropRatesLore = new ArrayList<>();
        dropRatesLore.add(ChatColor.GRAY + "Click to view drop rates");
        dropRatesLore.add(ChatColor.GRAY + "for this farm type");
        ItemStack dropRatesButton = createItem(Material.KNOWLEDGE_BOOK, 
                                             ChatColor.BLUE + "Drop Rates", dropRatesLore);
        inventory.setItem(47, dropRatesButton);
        
        List<String> upgradeLore = new ArrayList<>();
        upgradeLore.add(ChatColor.GRAY + "Click to upgrade this farm");
        upgradeLore.add(ChatColor.GRAY + "(Coming Soon)");
        ItemStack upgradeButton = createItem(Material.EXPERIENCE_BOTTLE, 
                                           ChatColor.YELLOW + "Upgrade", upgradeLore);
        inventory.setItem(51, upgradeButton);
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
                    
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(materialItem);
                        collected += amount;
                    } else {
                        player.getWorld().dropItem(player.getLocation(), materialItem);
                        collected += amount;
                    }
                }
            }
        }
        
        if (collected > 0) {
            farmInstance.clearStoredMaterials();
            plugin.getPlantationManager().savePlayerData(player.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "Collected " + collected + " materials!");
            refresh();
        } else {
            player.sendMessage(ChatColor.RED + "No materials to collect!");
        }
    }

    public void showDropRates() {
        player.closeInventory();
        
        List<MaterialDrop> drops = plugin.getPlantationManager().getFarmDrops(farmInstance.getFarmType());
        
        player.sendMessage(ChatColor.GOLD + "=== Drop Rates for " + farmInstance.getFarmType().getDisplayName() + " ===");
        for (MaterialDrop drop : drops) {
            String tierRoman = getTierRoman(drop.getTier());
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + 
                             drop.getMaterialType().getDisplayName() + " " + tierRoman + 
                             ChatColor.GRAY + ": " + ChatColor.GREEN + drop.getRate() + "%");
        }
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