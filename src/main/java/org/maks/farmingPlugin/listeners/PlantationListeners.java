package org.maks.farmingPlugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.gui.PlantationGUI;
import org.maks.farmingPlugin.gui.FarmUpgradeGUI;
import org.maks.farmingPlugin.gui.PlayerSettingsGUI;
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
        
        // Check for first join
        if (!plugin.getDatabaseManager().loadPlayerPlot(uuid).isPresent()) {
            player.sendMessage(ChatColor.GREEN + "Welcome to the Farming System!");
            player.sendMessage(ChatColor.YELLOW + "Use /plantation to visit your farm area!");
            
            // Give starter materials if configured
            if (plugin.getConfig().getBoolean("starter_kit.enabled", true)) {
                giveStarterKit(player);
            }
        } else {
            // Check farms that need attention
            checkFarmsNeedingAttention(player);
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
        
        if (!player.hasPermission("plantation.access")) {
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
            // This is a new farm location, need to determine instance
            instanceId = getNextAvailableInstance(player.getUniqueId(), farmType);
            
            if (instanceId == -1) {
                player.sendMessage(ChatColor.RED + "All " + farmType.getDisplayName() + 
                                 " instances are already created!");
                player.sendMessage(ChatColor.YELLOW + "Maximum instances: " + farmType.getMaxInstances());
                return;
            }
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

        // Process any pending harvests
        plugin.getPlantationManager().processFarmHarvest(farmInstance);
        
        // Open GUI
        PlantationGUI gui = new PlantationGUI(plugin, farmInstance, player);
        player.openInventory(gui.getInventory());
        
        // Update cooldown
        lastInteraction.put(player.getUniqueId(), System.currentTimeMillis());
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
        }
    }

    private void handlePlantationGUIClick(Player player, PlantationGUI gui, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        
        switch (displayName.toLowerCase()) {
            case "collect all" -> {
                gui.collectAllMaterials();
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            }
            case "drop rates" -> {
                gui.showDropRates();
                player.closeInventory();
            }
            case "upgrade farm" -> {
                FarmUpgradeGUI upgradeGui = new FarmUpgradeGUI(plugin, gui.getFarmInstance(), player);
                player.openInventory(upgradeGui.getInventory());
            }
            case "auto-collect" -> {
                gui.toggleAutoCollect();
                gui.refresh();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case "statistics" -> {
                gui.showStatistics();
                player.closeInventory();
            }
            case "settings" -> {
                PlayerSettingsGUI settingsGui = new PlayerSettingsGUI(plugin, player);
                player.openInventory(settingsGui.getInventory());
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
            case 10 -> gui.toggleSetting("autocollect");
            case 12 -> gui.toggleSetting("holograms");
            case 14 -> gui.toggleSetting("notifications");
            case 16 -> gui.toggleSetting("particles");
            case 28 -> gui.toggleSetting("inventory");
            case 31 -> gui.resetSettings();
            case 49 -> player.closeInventory();
        }
        
        if (slot != 49) {
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Save data when closing farm GUI
        if (event.getInventory().getHolder() instanceof PlantationGUI) {
            plugin.getPlantationManager().savePlayerData(player.getUniqueId());
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
        
        // Prevent placing on plantations
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

    private int getNextAvailableInstance(UUID playerUuid, FarmType farmType) {
        List<FarmInstance> playerFarms = plugin.getPlantationManager().getPlayerFarms(playerUuid);
        int maxInstances = farmType.getMaxInstances();
        
        for (int i = 1; i <= maxInstances; i++) {
            final int instanceId = i;
            boolean exists = playerFarms.stream()
                .anyMatch(farm -> farm.getFarmType() == farmType && farm.getInstanceId() == instanceId);
            
            if (!exists && plugin.getPlantationAreaManager().isInstanceAvailable(playerUuid, farmType, instanceId)) {
                return instanceId;
            }
        }
        
        return -1;
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
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GOLD + " " + farmType.getDisplayName() + " - LOCKED");
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "Requirements to unlock:");
        
        List<String> requirements = plugin.getPlantationManager().getUnlockRequirementsDisplay(farmType);
        for (String req : requirements) {
            player.sendMessage(req);
        }
        
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
    }

    private void giveStarterKit(Player player) {
        // Give some starter materials
        ItemStack starterItem = plugin.getMaterialManager()
            .createMaterial(org.maks.farmingPlugin.materials.MaterialType.PLANT_FIBER, 1, 10);
        
        player.getInventory().addItem(starterItem);
        player.sendMessage(ChatColor.GREEN + "You received a starter kit with 10x Plant Fiber!");
    }

    private void checkFarmsNeedingAttention(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        int needingAttention = 0;
        
        for (FarmInstance farm : farms) {
            if (farm.needsAttention()) {
                needingAttention++;
            }
        }
        
        if (needingAttention > 0) {
            player.sendMessage(ChatColor.YELLOW + "⚠ " + needingAttention + 
                             " farm(s) need your attention!");
            player.sendMessage(ChatColor.GRAY + "Use /plantation to visit your farms.");
        }
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
