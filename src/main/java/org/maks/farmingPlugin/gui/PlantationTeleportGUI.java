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
import org.bukkit.inventory.meta.SkullMeta;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;

import java.util.ArrayList;
import java.util.List;

public class PlantationTeleportGUI implements InventoryHolder {
    private final FarmingPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    public PlantationTeleportGUI(FarmingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        String title = ChatColor.DARK_GREEN + "🌱 Plantation Portal";
        this.inventory = Bukkit.createInventory(this, 45, title);

        setupGUI();
    }

    private void setupGUI() {
        fillBackground();
        addPlayerInfo();
        addTeleportButton();
        addFarmInfo();
        addControlButtons();
    }

    private void fillBackground() {
        ItemStack borderItem = createItem(Material.GREEN_STAINED_GLASS_PANE, " ", null);

        // Fill borders
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 36, borderItem);
        }
        for (int i = 9; i < 36; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }

        // Decorative pattern
        ItemStack decorItem = createItem(Material.LIME_STAINED_GLASS_PANE, " ", null);
        inventory.setItem(10, decorItem);
        inventory.setItem(16, decorItem);
        inventory.setItem(28, decorItem);
        inventory.setItem(34, decorItem);
    }

    private void addPlayerInfo() {
        // Player head with stats
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();

        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName(ChatColor.GOLD + player.getName() + "'s Stats");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━");

            // Get player stats
            double balance = plugin.getEconomyManager().getBalance(player);
            lore.add(ChatColor.GREEN + "💰 Balance: " + ChatColor.GOLD +
                    plugin.getEconomyManager().formatMoney(balance));

            // Get farm count
            List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
            lore.add(ChatColor.AQUA + "🌾 Active Farms: " + ChatColor.WHITE + farms.size());

            // Check if any farms ready
            long readyCount = farms.stream().filter(FarmInstance::isReadyForHarvest).count();
            if (readyCount > 0) {
                lore.add(ChatColor.GREEN + "✦ " + readyCount + " ready to harvest!");
            }

            lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━");

            skullMeta.setLore(lore);
            playerHead.setItemMeta(skullMeta);
        }

        inventory.setItem(13, playerHead);
    }

    private void addTeleportButton() {
        ItemStack teleportButton = createItem(Material.ENDER_PEARL,
                ChatColor.GREEN + "§l➤ TELEPORT TO PLANTATION",
                null);
        ItemMeta meta = teleportButton.getItemMeta();

        List<String> lore = new ArrayList<>();

        // Check level requirement
        int playerLevel = player.getLevel();
        int requiredLevel = plugin.getConfig().getInt("teleport.minimum_level", 85);

        if (playerLevel >= requiredLevel) {
            lore.add(ChatColor.GREEN + "✔ Level requirement met!");
            lore.add(ChatColor.GRAY + "Your level: " + ChatColor.WHITE + playerLevel);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to teleport!");
            lore.add(ChatColor.GRAY + "Travel to your plantation");

            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(ChatColor.RED + "✘ Level requirement not met!");
            lore.add(ChatColor.GRAY + "Your level: " + ChatColor.WHITE + playerLevel);
            lore.add(ChatColor.GRAY + "Required: " + ChatColor.RED + requiredLevel);
            lore.add("");
            lore.add(ChatColor.RED + "You must be at least level " + requiredLevel + "!");

            // Change to barrier if not enough level
            teleportButton.setType(Material.BARRIER);
            meta.setDisplayName(ChatColor.RED + "§l✘ CANNOT TELEPORT");
        }

        meta.setLore(lore);
        teleportButton.setItemMeta(meta);

        inventory.setItem(22, teleportButton);
    }

    private void addFarmInfo() {
        // Farming Shop
        ItemStack farmInfo = createItem(Material.EMERALD,
                ChatColor.GREEN + "💎 Farming Shop",
                null);
        ItemMeta meta = farmInfo.getItemMeta();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Access the farming shop!");
        lore.add(ChatColor.GRAY + "Buy seeds, tools, and more!");
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to open shop!");

        meta.setLore(lore);
        farmInfo.setItemMeta(meta);

        inventory.setItem(20, farmInfo);

        // NAPRAWKA: Usuń Quick Stats - nie potrzebne
    }

    private void addControlButtons() {
        // Info button
        ItemStack infoButton = createItem(Material.BOOK,
                ChatColor.YELLOW + "ℹ Information",
                null);
        ItemMeta infoMeta = infoButton.getItemMeta();

        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Learn about farming!");
        infoLore.add("");
        infoLore.add(ChatColor.WHITE + "• Grow crops over time");
        infoLore.add(ChatColor.WHITE + "• Harvest fruits to sell");
        infoLore.add(ChatColor.WHITE + "• Unlock new farm types");
        infoLore.add(ChatColor.WHITE + "• Upgrade for better yields");

        infoMeta.setLore(infoLore);
        infoButton.setItemMeta(infoMeta);

        inventory.setItem(30, infoButton);

        // NAPRAWKA: Usuń Commands button - nie potrzebne

        // Close button
        ItemStack closeButton = createItem(Material.BARRIER,
                ChatColor.RED + "✘ Close",
                null);
        ItemMeta closeMeta = closeButton.getItemMeta();

        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Close this menu");

        closeMeta.setLore(closeLore);
        closeButton.setItemMeta(closeMeta);

        inventory.setItem(40, closeButton);
    }

    public boolean canTeleport() {
        int playerLevel = player.getLevel();
        int requiredLevel = plugin.getConfig().getInt("teleport.minimum_level", 85);

        if (!plugin.getConfig().getBoolean("teleport.require_level", true)) {
            return true;
        }

        return playerLevel >= requiredLevel;
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

