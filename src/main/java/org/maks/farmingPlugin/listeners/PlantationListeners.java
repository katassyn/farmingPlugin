package org.maks.farmingPlugin.listeners;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.gui.PlantationGUI;

public class PlantationListeners implements Listener {
    private final FarmingPlugin plugin;

    public PlantationListeners(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        plugin.getPlantationManager().loadPlayerData(player.getUniqueId());
        plugin.getOfflineGrowthManager().onPlayerJoin(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        plugin.getOfflineGrowthManager().onPlayerQuit(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) return;
        
        if (!player.hasPermission("plantation.access")) {
            return;
        }

        FarmType farmType = FarmType.fromBlockType(block.getType());
        if (farmType == null) return;

        if (!isOnPlayerPlantation(player, block)) {
            player.sendMessage(ChatColor.RED + "You can only interact with farms on your own plantation!");
            return;
        }

        event.setCancelled(true);

        int instanceId = determineFarmInstance(player, block, farmType);
        FarmInstance farmInstance = plugin.getPlantationManager()
                .getFarmInstance(player.getUniqueId(), farmType, instanceId);

        if (farmInstance == null) {
            if (canCreateNewFarm(player, farmType)) {
                farmInstance = plugin.getPlantationManager()
                        .createFarmInstance(player.getUniqueId(), farmType, instanceId, block.getLocation());
                player.sendMessage(ChatColor.GREEN + "Created new " + farmType.getDisplayName() + " farm!");
            } else {
                player.sendMessage(ChatColor.RED + "You cannot create more instances of this farm type!");
                return;
            }
        }

        plugin.getPlantationManager().processFarmHarvest(farmInstance);
        
        PlantationGUI gui = new PlantationGUI(plugin, farmInstance, player);
        player.openInventory(gui.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (!(event.getInventory().getHolder() instanceof PlantationGUI gui)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();
        
        if (displayName.contains("Collect All")) {
            gui.collectAllMaterials();
        } else if (displayName.contains("Drop Rates")) {
            gui.showDropRates();
        } else if (displayName.contains("Upgrade")) {
            player.sendMessage(ChatColor.YELLOW + "Farm upgrades coming soon!");
        }
    }

    private boolean isOnPlayerPlantation(Player player, Block block) {
        return true;
    }

    private int determineFarmInstance(Player player, Block block, FarmType farmType) {
        return 1;
    }

    private boolean canCreateNewFarm(Player player, FarmType farmType) {
        long currentInstances = plugin.getPlantationManager()
                .getPlayerFarms(player.getUniqueId())
                .stream()
                .filter(farm -> farm.getFarmType() == farmType)
                .count();
        
        if (currentInstances >= farmType.getMaxInstances()) {
            return false;
        }

        if (farmType == FarmType.BERRY_ORCHARDS) {
            return true;
        }

        if (!plugin.getEconomyManager().hasBalance(player, farmType.getUnlockCost())) {
            player.sendMessage(ChatColor.RED + "You need " + 
                    plugin.getEconomyManager().formatMoney(farmType.getUnlockCost()) + 
                    " to unlock this farm type!");
            return false;
        }

        if (!plugin.getPlantationManager().canUnlockFarm(player.getUniqueId(), farmType)) {
            player.sendMessage(ChatColor.RED + "You don't have the required materials to unlock this farm!");
            return false;
        }

        if (plugin.getEconomyManager().withdrawMoney(player, farmType.getUnlockCost())) {
            player.sendMessage(ChatColor.GREEN + "Unlocked " + farmType.getDisplayName() + " for " + 
                    plugin.getEconomyManager().formatMoney(farmType.getUnlockCost()) + "!");
            return true;
        }

        return false;
    }
}