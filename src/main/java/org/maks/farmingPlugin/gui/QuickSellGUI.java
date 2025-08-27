package org.maks.farmingPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.fruits.FruitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuickSellGUI implements InventoryHolder {
    private final FarmingPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, ItemStack> fruitsToSell;
    private long totalValue;

    public QuickSellGUI(FarmingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.fruitsToSell = new HashMap<>();
        
        String title = ChatColor.GOLD + "⚡ Quick Sell - Fruits";
        this.inventory = Bukkit.createInventory(this, 54, title);
        
        setupGUI();
    }

    private void setupGUI() {
        fillBorders();
        addControlButtons();
        updateTotalDisplay();
    }

    private void fillBorders() {
        ItemStack borderItem = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", null);
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 45, borderItem);
        }
        
        // Sides
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }
        
        // Separator row
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, borderItem);
        }
    }

    private void addControlButtons() {
        // Info item
        ItemStack infoItem = createItem(Material.BOOK, 
            ChatColor.GOLD + "How to Quick Sell", null);
        ItemMeta infoMeta = infoItem.getItemMeta();
        
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Place your fruits in the");
        infoLore.add(ChatColor.GRAY + "empty slots above!");
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "Accepted Fruits:");
        for (FruitType fruit : FruitType.values()) {
            infoLore.add(ChatColor.GRAY + "• " + fruit.getDisplayName());
        }
        infoLore.add("");
        infoLore.add(ChatColor.GREEN + "Click 'Sell All' when ready!");
        
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(45, infoItem);
        
        // Total value display
        updateTotalDisplay();
        
        // Sell All button
        ItemStack sellButton = createItem(Material.EMERALD_BLOCK,
            ChatColor.GREEN + "§lSELL ALL", null);
        ItemMeta sellMeta = sellButton.getItemMeta();
        
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Click to sell all fruits");
        sellLore.add(ChatColor.GRAY + "in the inventory above!");
        sellLore.add("");
        sellLore.add(ChatColor.YELLOW + "Total Value: " + ChatColor.GOLD + 
                    plugin.getEconomyManager().formatMoney(totalValue));
        
        sellMeta.setLore(sellLore);
        sellMeta.addEnchant(Enchantment.DURABILITY, 1, true);
        sellMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        sellButton.setItemMeta(sellMeta);
        
        inventory.setItem(49, sellButton);
        
        // Cancel/Close button
        ItemStack cancelButton = createItem(Material.BARRIER,
            ChatColor.RED + "Close", null);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Close without selling");
        cancelLore.add(ChatColor.GRAY + "Items will be returned!");
        
        cancelMeta.setLore(cancelLore);
        cancelButton.setItemMeta(cancelMeta);
        
        inventory.setItem(53, cancelButton);
        
        // Statistics
        ItemStack statsItem = createItem(Material.PAPER,
            ChatColor.AQUA + "Sell Statistics", null);
        ItemMeta statsMeta = statsItem.getItemMeta();
        
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Track your earnings!");
        statsLore.add("");
        
        // Get player's total earnings from database
        try {
            String sql = "SELECT total_money_earned FROM farming_player_stats WHERE uuid = ?";
            var stmt = plugin.getDatabaseManager().prepareStatement(sql);
            stmt.setString(1, player.getUniqueId().toString());
            var rs = stmt.executeQuery();
            
            if (rs.next()) {
                double earned = rs.getDouble("total_money_earned");
                statsLore.add(ChatColor.GREEN + "Total Earned: " + ChatColor.GOLD + 
                            plugin.getEconomyManager().formatMoney(earned));
            }
            
            rs.close();
            stmt.close();
        } catch (Exception e) {
            statsLore.add(ChatColor.RED + "Error loading stats");
        }
        
        statsLore.add(ChatColor.GRAY + "Current Balance: " + ChatColor.GOLD + 
                    plugin.getEconomyManager().formatMoney(
                        plugin.getEconomyManager().getBalance(player)));
        
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        
        inventory.setItem(46, statsItem);
    }

    public void updateTotalDisplay() {
        calculateTotal();
        
        ItemStack totalItem = createItem(Material.SUNFLOWER,
            ChatColor.GOLD + "Total Value", null);
        ItemMeta totalMeta = totalItem.getItemMeta();
        
        List<String> totalLore = new ArrayList<>();
        totalLore.add(ChatColor.GRAY + "Items ready to sell:");
        
        Map<FruitType, Integer> fruitCounts = new HashMap<>();
        
        // Count fruits
        for (int slot = 10; slot <= 34; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) continue; // Skip borders
            
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                
                for (FruitType fruit : FruitType.values()) {
                    if (ChatColor.stripColor(fruit.getDisplayName()).equals(displayName)) {
                        fruitCounts.merge(fruit, item.getAmount(), Integer::sum);
                        break;
                    }
                }
            }
        }
        
        if (fruitCounts.isEmpty()) {
            totalLore.add(ChatColor.GRAY + "No fruits placed yet!");
        } else {
            for (Map.Entry<FruitType, Integer> entry : fruitCounts.entrySet()) {
                FruitType fruit = entry.getKey();
                int count = entry.getValue();
                long value = fruit.getSellPrice() * count;
                
                totalLore.add(ChatColor.GRAY + "• " + count + "x " + 
                            fruit.getDisplayName() + ChatColor.GRAY + " = " + 
                            ChatColor.GOLD + plugin.getEconomyManager().formatMoney(value));
            }
        }
        
        totalLore.add("");
        totalLore.add(ChatColor.YELLOW + "Total: " + ChatColor.GOLD + ChatColor.BOLD + 
                    plugin.getEconomyManager().formatMoney(totalValue));
        
        totalMeta.setLore(totalLore);
        
        if (totalValue > 0) {
            totalMeta.addEnchant(Enchantment.DURABILITY, 1, true);
            totalMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        totalItem.setItemMeta(totalMeta);
        inventory.setItem(4, totalItem);
    }

    private void calculateTotal() {
        totalValue = 0;
        fruitsToSell.clear();
        
        for (int slot = 10; slot <= 34; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) continue; // Skip borders
            
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()) {
                String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                
                for (FruitType fruit : FruitType.values()) {
                    if (ChatColor.stripColor(fruit.getDisplayName()).equals(displayName)) {
                        fruitsToSell.put(slot, item);
                        totalValue += fruit.getSellPrice() * item.getAmount();
                        break;
                    }
                }
            }
        }
    }

    public void sellAll() {
        // Always recalculate to get the most up-to-date state
        calculateTotal();
        
        if (totalValue <= 0 || fruitsToSell.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No fruits to sell!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        // Give money to player
        plugin.getEconomyManager().depositMoney(player, totalValue);
        
        // Update stats
        plugin.getDatabaseManager().updatePlayerStats(player.getUniqueId(), 
                                                      "total_money_earned", 
                                                      (long) totalValue);
        
        // Clear sold items
        for (int slot : fruitsToSell.keySet()) {
            inventory.setItem(slot, null);
        }
        
        // Effects
        player.sendMessage(ChatColor.GREEN + "✦ Sold fruits for " + 
                         ChatColor.GOLD + plugin.getEconomyManager().formatMoney(totalValue) + 
                         ChatColor.GREEN + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        
        // Particles
        player.getWorld().spawnParticle(
            org.bukkit.Particle.VILLAGER_HAPPY,
            player.getLocation().add(0, 1, 0),
            30, 0.5, 0.5, 0.5, 0.1
        );
        
        // Reset display
        totalValue = 0;
        fruitsToSell.clear();
        updateTotalDisplay();
        
        // Refresh sell button
        addControlButtons();
    }

    public void returnItems() {
        // Return all items to player
        for (int slot = 10; slot <= 34; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) continue;
            
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                for (ItemStack overflowItem : overflow.values()) {
                    player.getWorld().dropItem(player.getLocation(), overflowItem);
                }
            }
        }
    }

    public boolean isFruit(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        
        for (FruitType fruit : FruitType.values()) {
            if (ChatColor.stripColor(fruit.getDisplayName()).equals(displayName)) {
                return true;
            }
        }
        
        return false;
    }

    public boolean isSellingSlot(int slot) {
        // Check if slot is in the selling area (rows 2-4, excluding borders)
        if (slot >= 10 && slot <= 34) {
            return slot % 9 != 0 && slot % 9 != 8;
        }
        return false;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
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
}
