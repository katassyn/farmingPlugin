package org.maks.farmingPlugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.gui.PlantationGUI;
import org.maks.farmingPlugin.gui.FarmUpgradeGUI;
import org.maks.farmingPlugin.gui.PlayerSettingsGUI;
import org.maks.farmingPlugin.gui.QuickSellGUI;
import org.maks.farmingPlugin.gui.PlantationTeleportGUI;
import org.maks.farmingPlugin.managers.PlantationAreaManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlantationListeners implements Listener {
    private final FarmingPlugin plugin;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();
    private final Map<UUID, FarmSelectionMode> farmSelectionModes = new ConcurrentHashMap<>();

    public PlantationListeners(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Load player data asynchronously
        plugin.getPlantationManager().loadPlayerData(uuid);
        plugin.getOfflineGrowthManager().onPlayerJoin(uuid);
        
        // Check for first join (don't give items)
        if (!plugin.getDatabaseManager().loadPlayerPlot(uuid).isPresent()) {
            player.sendMessage(ChatColor.GREEN + "Welcome to the Farming System!");
            player.sendMessage(ChatColor.YELLOW + "Visit the Farm NPC to access your plantation!");
            player.sendMessage(ChatColor.YELLOW + "You must be level 85 to start farming!");
        } else {
            // Check farms that need attention
            checkFarmsNeedingAttention(player);
        }

        if (plugin.getConfig().getBoolean("plantation.rebuild_on_join", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getPlantationAreaManager().regeneratePlayerArea(event.getPlayer());
            }, 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        plugin.getOfflineGrowthManager().onPlayerQuit(uuid);
        
        // Remove from selection mode
        farmSelectionModes.remove(uuid);
        
        // Clean up holograms
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removePlayerHolograms(uuid);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) return;
        
        // Check for cooldown
        if (isOnCooldown(player)) {
            return;
        }
        
        if (!player.hasPermission("plantation.use")) {
            return;
        }

        FarmType farmType = FarmType.fromBlockType(block.getType());
        if (farmType == null) return;

        // Check if on player's plantation
        if (!plugin.getPlantationAreaManager().isLocationInPlantation(player.getUniqueId(), block.getLocation())) {
            // Check if it's another player's farm
            UUID ownerUuid = findPlantationOwner(block.getLocation());
            if (ownerUuid != null) {
                Player owner = plugin.getServer().getPlayer(ownerUuid);
                String ownerName = owner != null ? owner.getName() : "another player";
                player.sendMessage(ChatColor.RED + "This farm belongs to " + ownerName + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        event.setCancelled(true);

        // Handle farm selection mode
        FarmSelectionMode selectionMode = farmSelectionModes.get(player.getUniqueId());
        if (selectionMode != null) {
            handleFarmSelection(player, block, farmType, selectionMode);
            return;
        }

        // Get the instance from location
        int instanceId = plugin.getPlantationAreaManager()
            .getFarmInstanceFromLocation(player.getUniqueId(), farmType, block.getLocation());

        if (instanceId == -1) {
            player.sendMessage(ChatColor.RED + "Please use one of the marked spots for "
                               + farmType.getDisplayName() + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        FarmInstance farmInstance = plugin.getPlantationManager()
                .getFarmInstance(player.getUniqueId(), farmType, instanceId);

        if (farmInstance == null) {
            // Check if player can create this farm type
            if (!canCreateFarm(player, farmType)) {
                showUnlockRequirements(player, farmType);
                return;
            }
            
            // Create new farm instance
            Location anchorLocation = plugin.getPlantationAreaManager()
                .getOrCreateFarmAnchor(player.getUniqueId(), farmType, instanceId);
            
            if (anchorLocation == null) {
                player.sendMessage(ChatColor.RED + "Failed to create farm anchor!");
                return;
            }
            
            farmInstance = plugin.getPlantationManager()
                    .createFarmInstance(player.getUniqueId(), farmType, instanceId, anchorLocation);
            
            player.sendMessage(ChatColor.GREEN + "✔ Created " + farmType.getDisplayName() + 
                             " instance #" + instanceId + "!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            
            // Particles for new farm
            player.getWorld().spawnParticle(
                org.bukkit.Particle.VILLAGER_HAPPY,
                anchorLocation.clone().add(0.5, 1, 0.5),
                30, 0.5, 0.5, 0.5, 0.1
            );
        }

        // Harvest instantly if ready, otherwise open the GUI
        if (farmInstance.isReadyForHarvest()) {
            plugin.getPlantationManager().processFarmHarvest(farmInstance);
        } else {
            PlantationGUI gui = new PlantationGUI(plugin, farmInstance, player);
            player.openInventory(gui.getInventory());
        }

        // Update cooldown
        lastInteraction.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRightClickLockedSign(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        BlockState st = b.getState();
        if (!(st instanceof Sign sign)) return;

        var pdc = sign.getPersistentDataContainer();
        NamespacedKey K_LOCKED = new NamespacedKey(plugin, "locked_farm");
        NamespacedKey K_TYPE = new NamespacedKey(plugin, "farm_type");
        NamespacedKey K_ID = new NamespacedKey(plugin, "instance_id");

        if (pdc.getOrDefault(K_LOCKED, PersistentDataType.INTEGER, 0) != 1) return;

        String typeId = pdc.get(K_TYPE, PersistentDataType.STRING);
        Integer instanceId = pdc.get(K_ID, PersistentDataType.INTEGER);
        if (typeId == null || instanceId == null) return;

        FarmType type = FarmType.fromId(typeId);
        if (type == null) return;

        Player player = e.getPlayer();
        e.setCancelled(true);

        boolean unlocked = plugin.getPlantationManager().attemptUnlock(player, type, instanceId);
        if (unlocked) {
            plugin.getPlantationAreaManager().placeFarmBlock(b.getLocation(), type);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                FarmInstance fi = plugin.getPlantationManager().getFarmInstance(player.getUniqueId(), type, instanceId);
                if (fi != null && plugin.getHologramManager() != null) {
                    plugin.getHologramManager().updateHologram(fi);
                }
            }, 2L);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            showUnlockRequirements(player, type);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (event.getInventory().getHolder() instanceof PlantationGUI gui) {
            event.setCancelled(true);
            handlePlantationGUIClick(player, gui, event);
        } else if (event.getInventory().getHolder() instanceof FarmUpgradeGUI upgradeGui) {
            event.setCancelled(true);
            handleUpgradeGUIClick(player, upgradeGui, event);
        } else if (event.getInventory().getHolder() instanceof PlayerSettingsGUI settingsGui) {
            event.setCancelled(true);
            handleSettingsGUIClick(player, settingsGui, event);
        } else if (event.getInventory().getHolder() instanceof QuickSellGUI sellGui) {
            handleQuickSellGUIClick(player, sellGui, event);
        } else if (event.getInventory().getHolder() instanceof PlantationTeleportGUI teleportGui) {
            event.setCancelled(true);
            handleTeleportGUIClick(player, teleportGui, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof QuickSellGUI) {
            // Allow dragging only in the selling slots
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= event.getInventory().getSize()) continue;
                if (slot < 10 || slot > 34 || slot % 9 == 0 || slot % 9 == 8) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void handlePlantationGUIClick(Player player, PlantationGUI gui, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        
        switch (displayName.toLowerCase()) {
            case "drop rates" -> {
                gui.showDropRates();
                player.closeInventory();
            }
            case "quick sell" -> {
                QuickSellGUI sellGui = new QuickSellGUI(plugin, player);
                player.openInventory(sellGui.getInventory());
            }
            case "upgrade farm" -> {
                FarmUpgradeGUI upgradeGui = new FarmUpgradeGUI(plugin, gui.getFarmInstance(), player);
                player.openInventory(upgradeGui.getInventory());
            }
            case "statistics" -> {
                gui.showStatistics();
                player.closeInventory();
            }
            case "settings" -> {
                PlayerSettingsGUI settingsGui = new PlayerSettingsGUI(plugin, player);
                player.openInventory(settingsGui.getInventory());
            }
            case "help" -> {
                sendHelpMessage(player);
                player.closeInventory();
            }
        }
    }

    private void handleUpgradeGUIClick(Player player, FarmUpgradeGUI gui, InventoryClickEvent event) {
        int slot = event.getSlot();
        
        // Handle upgrade purchases based on slot
        if (slot == 20) { // Storage upgrade
            gui.purchaseUpgrade("storage");
        } else if (slot == 22) { // Speed upgrade
            gui.purchaseUpgrade("speed");
        } else if (slot == 24) { // Quality upgrade
            gui.purchaseUpgrade("quality");
        } else if (slot == 40) { // Level up
            gui.attemptLevelUp();
        } else if (slot == 49) { // Back button
            PlantationGUI plantGui = new PlantationGUI(plugin, gui.getFarmInstance(), player);
            player.openInventory(plantGui.getInventory());
        }
    }

    private void handleSettingsGUIClick(Player player, PlayerSettingsGUI gui, InventoryClickEvent event) {
        int slot = event.getSlot();
        
        switch (slot) {
            case 12 -> gui.toggleSetting("holograms");
            case 14 -> gui.toggleSetting("notifications");
            case 16 -> gui.toggleSetting("particles");
            case 31 -> gui.resetSettings();
            case 49 -> player.closeInventory();
        }
        
        if (slot != 49) {
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void handleQuickSellGUIClick(Player player, QuickSellGUI gui, InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        int rawSlot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Allow interaction with the player's own inventory
        if (event.getClickedInventory().equals(player.getInventory())) {
            // Prevent shift-clicking non-fruits into the GUI
            if (event.isShiftClick() && clicked != null && !gui.isFruit(clicked)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only fruits can be sold here!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (event.isShiftClick()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, gui::updateTotalDisplay, 1L);
            }
            return; // Don't cancel normal inventory actions
        }

        // Handle selling area (slots 10-34, excluding borders)
        if (gui.isSellingSlot(rawSlot)) {
            if (cursor != null && cursor.getType() != Material.AIR && !gui.isFruit(cursor)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Only fruits can be placed here!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, gui::updateTotalDisplay, 1L);
            return;
        }

        // Cancel clicks on control buttons and borders
        event.setCancelled(true);

        // Handle control buttons
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.equals("SELL ALL")) {
            gui.sellAll();
        } else if (displayName.equals("Close")) {
            gui.returnItems();
            player.closeInventory();
        }
    }

    private void handleTeleportGUIClick(Player player, PlantationTeleportGUI gui, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 22) {
            if (gui.canTeleport()) {
                player.closeInventory();
                player.performCommand("plantation tp");
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                int requiredLevel = plugin.getConfig().getInt("teleport.minimum_level", 85);
                player.sendMessage(ChatColor.RED + "You must be at least level " + requiredLevel + " to teleport!");
            }
        } else if (slot == 40) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Save data when closing farm GUI
        if (event.getInventory().getHolder() instanceof PlantationGUI) {
            plugin.getPlantationManager().savePlayerData(player.getUniqueId());
        } else if (event.getInventory().getHolder() instanceof QuickSellGUI gui) {
            // Return unsold items
            gui.returnItems();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Prevent breaking farm blocks
        FarmType farmType = FarmType.fromBlockType(block.getType());
        if (farmType != null) {
            // Check if it's a farm block on any plantation
            UUID owner = findPlantationOwner(block.getLocation());
            if (owner != null) {
                event.setCancelled(true);
                
                if (!owner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "You cannot break other players' farms!");
                } else {
                    player.sendMessage(ChatColor.RED + "Right-click to interact with your farm!");
                }
                
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
        
        // Prevent building on plantations
        if (plugin.getConfig().getBoolean("plantations.protection.block_build", true)) {
            UUID owner = findPlantationOwner(block.getLocation());
            if (owner != null && !owner.equals(player.getUniqueId()) && 
                !player.hasPermission("plantation.admin.build")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot build on other players' plantations!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Prevent placing on other players' plantations
        if (plugin.getConfig().getBoolean("plantations.protection.block_build", true)) {
            UUID owner = findPlantationOwner(block.getLocation());
            if (owner != null && !owner.equals(player.getUniqueId()) &&
                !player.hasPermission("plantation.admin.build")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot build on other players' plantations!");
                return;
            }
        }

        // Block placing farm blocks on any plantation
        if (plugin.getConfig().getBoolean("plantations.protection.block_farm_blocks", true)) {
            if (FarmType.fromBlockType(block.getType()) != null) {
                UUID owner = findPlantationOwner(block.getLocation());
                if (owner != null) {
                    event.setCancelled(true);
                    if (owner.equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "You cannot manually place farm blocks. Use the designated slots.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You cannot build on another player's plantation!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Prevent PvP on plantations if configured
        if (plugin.getConfig().getBoolean("plantations.protection.block_pvp", false)) {
            if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
                Location loc = event.getEntity().getLocation();
                UUID owner = findPlantationOwner(loc);
                
                if (owner != null) {
                    event.setCancelled(true);
                    ((Player) event.getDamager()).sendMessage(ChatColor.RED + "PvP is disabled on plantations!");
                }
            }
        }
    }

    // Helper methods
    private boolean isOnCooldown(Player player) {
        Long lastTime = lastInteraction.get(player.getUniqueId());
        if (lastTime == null) return false;
        
        long cooldown = 500; // 500ms cooldown
        return System.currentTimeMillis() - lastTime < cooldown;
    }

    private UUID findPlantationOwner(Location location) {
        // This would need to check all player plantations
        // For now, simplified implementation
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getPlantationAreaManager().isLocationInPlantation(online.getUniqueId(), location)) {
                return online.getUniqueId();
            }
        }
        return null;
    }

    private boolean canCreateFarm(Player player, FarmType farmType) {
        // Berry Orchards is always available
        if (farmType == FarmType.BERRY_ORCHARDS) {
            return true;
        }
        
        // Check if already unlocked
        if (plugin.getDatabaseManager().isFarmUnlocked(player.getUniqueId(), farmType.getId())) {
            return true;
        }
        
        // Check if can unlock now
        if (plugin.getPlantationManager().canUnlockFarm(player.getUniqueId(), farmType)) {
            // Try to unlock
            return plugin.getPlantationManager().unlockFarm(player.getUniqueId(), farmType);
        }
        
        return false;
    }

    private void showUnlockRequirements(Player player, FarmType farmType) {
        player.sendMessage(ChatColor.RED + "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GOLD + " " + farmType.getDisplayName() + " - LOCKED");
        player.sendMessage(ChatColor.RED + "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "Requirements to unlock:");
        
        List<String> requirements = plugin.getPlantationManager().getUnlockRequirementsDisplay(farmType);
        for (String req : requirements) {
            player.sendMessage(req);
        }
        
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
    }

    private void checkFarmsNeedingAttention(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        int readyToHarvest = 0;
        
        for (FarmInstance farm : farms) {
            if (farm.isReadyForHarvest()) {
                readyToHarvest++;
            }
        }
        
        if (readyToHarvest > 0) {
            player.sendMessage(ChatColor.GREEN + "✦ " + readyToHarvest + 
                             " farm(s) ready to harvest!");
            player.sendMessage(ChatColor.GRAY + "Use /plantation to visit your farms.");
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══ Farming Guide ═══");
        player.sendMessage(ChatColor.YELLOW + "How to farm:");
        player.sendMessage(ChatColor.GRAY + "1. Wait for the harvest timer");
        player.sendMessage(ChatColor.GRAY + "2. Right-click the farm block");
        player.sendMessage(ChatColor.GRAY + "3. Collect fruits that drop");
        player.sendMessage(ChatColor.GRAY + "4. Sell fruits for money");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Special Materials:");
        player.sendMessage(ChatColor.GRAY + "• Rare materials drop occasionally");
        player.sendMessage(ChatColor.GRAY + "• Use them to unlock new farms");
        player.sendMessage(ChatColor.GRAY + "• Higher level = better drops");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Tip: Use /plantation quicksell to sell fruits!");
    }

    private void handleFarmSelection(Player player, Block block, FarmType farmType, FarmSelectionMode mode) {
        // Handle special farm selection modes (for future features)
        farmSelectionModes.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Selected " + farmType.getDisplayName() + " at " +
                         block.getLocation().getBlockX() + ", " + 
                         block.getLocation().getBlockY() + ", " + 
                         block.getLocation().getBlockZ());
    }

    // Farm selection mode for future features
    private static class FarmSelectionMode {
        final String mode;
        final Object data;
        
        FarmSelectionMode(String mode, Object data) {
            this.mode = mode;
            this.data = data;
        }
    }
}
