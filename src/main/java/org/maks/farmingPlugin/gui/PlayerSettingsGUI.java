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

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsGUI implements InventoryHolder {
    private final FarmingPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    
    // Current settings
    private boolean hologramsEnabled;
    private boolean notificationsEnabled;
    private boolean particleEffectsEnabled;

    public PlayerSettingsGUI(FarmingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        
        // Load current settings
        loadCurrentSettings();
        
        String title = ChatColor.DARK_PURPLE + "⚙ Plantation Settings";
        this.inventory = Bukkit.createInventory(this, 54, title);
        
        setupGUI();
    }

    private void loadCurrentSettings() {
        hologramsEnabled = plugin.getDatabaseManager()
            .getPlayerBooleanSetting(player.getUniqueId(), "hologram_enabled", true);
        notificationsEnabled = plugin.getDatabaseManager()
            .getPlayerBooleanSetting(player.getUniqueId(), "notifications_enabled", true);
        particleEffectsEnabled = plugin.getDatabaseManager()
            .getPlayerBooleanSetting(player.getUniqueId(), "particle_effects_enabled", true);
    }

    private void setupGUI() {
        fillBackground();
        addSettingToggles();
        addInfoItems();
        addControlButtons();
    }

    private void fillBackground() {
        ItemStack backgroundItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        
        // Fill entire inventory with background
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, backgroundItem);
        }
    }

    private void addSettingToggles() {
        // Holograms Setting
        addToggleSetting(12, Material.NAME_TAG,
                        "&bHolograms",
                        hologramsEnabled,
                        "&7Shows floating text above",
                        "&7your farms with information",
                        "",
                        "&eHelps track farm status!");

        // Notifications Setting
        addToggleSetting(14, Material.BELL,
                        "&eNotifications",
                        notificationsEnabled,
                        "&7Receive chat notifications",
                        "&7about farm events",
                        "",
                        "&eStay informed about harvests!");

        // Particle Effects Setting
        addToggleSetting(16, Material.BLAZE_POWDER,
                        "&dParticle Effects",
                        particleEffectsEnabled,
                        "&7Shows particle effects for",
                        "&7various farm activities",
                        "",
                        "&eMakes farming more visual!");

        // Statistics Display
        addStatisticsItem(30);

        // Farm Summary
        addFarmSummaryItem(32);
    }

    private void addToggleSetting(int slot, Material material, String name, boolean enabled,
                                 String... description) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        
        String status = enabled ? "&a&lENABLED" : "&c&lDISABLED";
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name + " &7- " + status));
        
        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle!");
        
        meta.setLore(lore);
        
        if (enabled) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        
        // Add icon next to toggle
        ItemStack icon = createItem(material, 
                                   ChatColor.translateAlternateColorCodes('&', name), 
                                   null);
        inventory.setItem(slot + 9, icon);
    }

    private void addStatisticsItem(int slot) {
        ItemStack statsItem = createItem(Material.BOOK,
                                        "&6📊 Your Statistics",
                                        null);
        ItemMeta meta = statsItem.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        
        // Get player statistics from database
        try {
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";
            var stmt = plugin.getDatabaseManager().prepareStatement(sql);
            stmt.setString(1, player.getUniqueId().toString());
            var rs = stmt.executeQuery();
            
            if (rs.next()) {
                lore.add(ChatColor.GRAY + "Total Farms: " + ChatColor.WHITE + 
                        rs.getInt("total_farms_created"));
                lore.add(ChatColor.GRAY + "Total Harvests: " + ChatColor.GREEN + 
                        String.format("%,d", rs.getLong("total_harvests")));
                lore.add(ChatColor.GRAY + "Materials Collected: " + ChatColor.AQUA + 
                        String.format("%,d", rs.getLong("total_materials_collected")));
                lore.add(ChatColor.GRAY + "Money Spent: " + ChatColor.GOLD + 
                        plugin.getEconomyManager().formatMoney(rs.getDouble("total_money_spent")));
                lore.add(ChatColor.GRAY + "Money Earned: " + ChatColor.GOLD + 
                        plugin.getEconomyManager().formatMoney(rs.getDouble("total_money_earned")));
                
                int playMinutes = rs.getInt("play_time_minutes");
                lore.add(ChatColor.GRAY + "Play Time: " + ChatColor.YELLOW + 
                        formatPlayTime(playMinutes));
            } else {
                lore.add(ChatColor.GRAY + "No statistics available yet!");
            }
            
            rs.close();
            stmt.close();
        } catch (Exception e) {
            lore.add(ChatColor.RED + "Error loading statistics!");
        }
        
        meta.setLore(lore);
        statsItem.setItemMeta(meta);
        
        inventory.setItem(slot, statsItem);
    }

    private void addFarmSummaryItem(int slot) {
        ItemStack summaryItem = createItem(Material.EMERALD,
                                          "&a🌱 Farm Summary",
                                          null);
        ItemMeta meta = summaryItem.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        var farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        if (farms.isEmpty()) {
            lore.add(ChatColor.GRAY + "You don't have any farms yet!");
        } else {
            lore.add(ChatColor.GREEN + "Active Farms: " + ChatColor.WHITE + farms.size());
            lore.add("");
            
            // Count by type
            for (org.maks.farmingPlugin.farms.FarmType type : org.maks.farmingPlugin.farms.FarmType.values()) {
                long count = farms.stream()
                    .filter(f -> f.getFarmType() == type)
                    .count();
                
                if (count > 0) {
                    lore.add(ChatColor.GRAY + "• " + type.getDisplayName() + ": " + 
                            ChatColor.WHITE + count + "/" + type.getMaxInstances());
                }
            }
            
            lore.add("");
            
            // Farms ready to harvest
            long readyToHarvest = farms.stream()
                .filter(f -> f.isReadyForHarvest())
                .count();
            
            if (readyToHarvest > 0) {
                lore.add(ChatColor.GREEN + "✦ " + readyToHarvest + " farms ready to harvest!");
            } else {
                lore.add(ChatColor.YELLOW + "No farms ready to harvest yet");
            }
        }
        
        meta.setLore(lore);
        summaryItem.setItemMeta(meta);
        
        inventory.setItem(slot, summaryItem);
    }

    private void addInfoItems() {
        // Tips
        ItemStack tipsItem = createItem(Material.KNOWLEDGE_BOOK,
                                       "&e💡 Tips & Tricks",
                                       null);
        ItemMeta tipsMeta = tipsItem.getItemMeta();
        
        List<String> tipsLore = new ArrayList<>();
        tipsLore.add(ChatColor.GRAY + "• Higher level farms produce more");
        tipsLore.add(ChatColor.GRAY + "• Upgrades stack multiplicatively");
        tipsLore.add(ChatColor.GRAY + "• Check farms regularly for fruits");
        tipsLore.add(ChatColor.GRAY + "• Rare materials drop occasionally");
        tipsLore.add(ChatColor.GRAY + "• Quality upgrades increase rare drops");
        tipsLore.add(ChatColor.GRAY + "• Use /plantation quicksell to sell");
        
        tipsMeta.setLore(tipsLore);
        tipsItem.setItemMeta(tipsMeta);
        
        inventory.setItem(25, tipsItem);

        // Quick Sell Button
        ItemStack quickSellItem = createItem(Material.EMERALD,
                                            "&aQuick Sell",
                                            null);
        ItemMeta sellMeta = quickSellItem.getItemMeta();
        
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Open the Quick Sell menu");
        sellLore.add(ChatColor.GRAY + "to sell your fruits!");
        sellLore.add("");
        sellLore.add(ChatColor.YELLOW + "Click to open!");
        
        sellMeta.setLore(sellLore);
        quickSellItem.setItemMeta(sellMeta);
        
        inventory.setItem(22, quickSellItem);
    }

    private void addControlButtons() {
        // Reset Settings Button
        ItemStack resetButton = createItem(Material.TNT,
                                          "&c⚠ Reset Settings",
                                          null);
        ItemMeta resetMeta = resetButton.getItemMeta();
        
        List<String> resetLore = new ArrayList<>();
        resetLore.add(ChatColor.GRAY + "Reset all settings to default");
        resetLore.add(ChatColor.RED + "This cannot be undone!");
        resetLore.add("");
        resetLore.add(ChatColor.YELLOW + "Click to reset");
        
        resetMeta.setLore(resetLore);
        resetButton.setItemMeta(resetMeta);
        
        inventory.setItem(31, resetButton);

        // Close Button
        ItemStack closeButton = createItem(Material.BARRIER,
                                          "&cClose",
                                          null);
        ItemMeta closeMeta = closeButton.getItemMeta();
        
        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Close this menu");
        
        closeMeta.setLore(closeLore);
        closeButton.setItemMeta(closeMeta);
        
        inventory.setItem(49, closeButton);

        // Help Button
        ItemStack helpButton = createItem(Material.PAPER,
                                         "&b❓ Help",
                                         null);
        ItemMeta helpMeta = helpButton.getItemMeta();
        
        List<String> helpLore = new ArrayList<>();
        helpLore.add(ChatColor.GRAY + "Need help with plantations?");
        helpLore.add(ChatColor.GRAY + "Check our wiki or ask staff!");
        helpLore.add("");
        helpLore.add(ChatColor.AQUA + "/plantation help");
        
        helpMeta.setLore(helpLore);
        helpButton.setItemMeta(helpMeta);
        
        inventory.setItem(53, helpButton);
    }

    public void toggleSetting(String setting) {
        switch (setting.toLowerCase()) {
            case "holograms" -> {
                hologramsEnabled = !hologramsEnabled;
                plugin.getDatabaseManager().savePlayerSetting(
                    player.getUniqueId(), "hologram_enabled", hologramsEnabled);
                
                if (!hologramsEnabled && plugin.getHologramManager() != null) {
                    plugin.getHologramManager().removePlayerHolograms(player.getUniqueId());
                }
                
                player.sendMessage(ChatColor.YELLOW + "Holograms " + 
                    (hologramsEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            }
            case "notifications" -> {
                notificationsEnabled = !notificationsEnabled;
                plugin.getDatabaseManager().savePlayerSetting(
                    player.getUniqueId(), "notifications_enabled", notificationsEnabled);
                
                player.sendMessage(ChatColor.YELLOW + "Notifications " + 
                    (notificationsEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            }
            case "particles" -> {
                particleEffectsEnabled = !particleEffectsEnabled;
                plugin.getDatabaseManager().savePlayerSetting(
                    player.getUniqueId(), "particle_effects_enabled", particleEffectsEnabled);
                
                player.sendMessage(ChatColor.YELLOW + "Particle effects " + 
                    (particleEffectsEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            }
        }
        
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void resetSettings() {
        // Reset all settings to default
        hologramsEnabled = true;
        notificationsEnabled = true;
        particleEffectsEnabled = true;
        
        // Save to database
        plugin.getDatabaseManager().savePlayerSetting(player.getUniqueId(), "hologram_enabled", true);
        plugin.getDatabaseManager().savePlayerSetting(player.getUniqueId(), "notifications_enabled", true);
        plugin.getDatabaseManager().savePlayerSetting(player.getUniqueId(), "particle_effects_enabled", true);
        
        player.sendMessage(ChatColor.GREEN + "✔ All settings have been reset to default!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        
        refresh();
    }

    public void refresh() {
        inventory.clear();
        setupGUI();
    }

    private String formatPlayTime(int minutes) {
        int hours = minutes / 60;
        int days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else {
            return minutes + "m";
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

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
