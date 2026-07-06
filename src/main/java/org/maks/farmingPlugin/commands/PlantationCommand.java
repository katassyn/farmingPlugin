package org.maks.farmingPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.gui.PlantationGUI;
import org.maks.farmingPlugin.gui.PlayerSettingsGUI;
import org.maks.farmingPlugin.gui.QuickSellGUI;
import org.maks.farmingPlugin.gui.PlantationTeleportGUI;
import org.maks.farmingPlugin.materials.MaterialType;
import org.maks.farmingPlugin.managers.PlantationAreaManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class PlantationCommand implements CommandExecutor, TabCompleter {
    private final FarmingPlugin plugin;
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();

    public PlantationCommand(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        if (args.length == 0) {
            // Only check permissions and level for GUI opening
            if (!player.hasPermission("plantation.use") && !player.hasPermission("plantation.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (player.getLevel() < 85) {
                player.sendMessage(ChatColor.RED + "You must be at least level 85!");
                return true;
            }
            
            PlantationTeleportGUI gui = new PlantationTeleportGUI(plugin, player);
            player.openInventory(gui.getInventory());
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "tp", "teleport", "visit" -> teleportToPlantation(player);
            case "info" -> showPlantationInfo(player);
            case "stats", "statistics" -> showDetailedStats(player);
            case "list" -> listPlayerFarms(player);
            case "settings" -> openSettingsGUI(player);
            case "quicksell", "sell" -> openQuickSellGUI(player);
            case "help" -> sendHelpMessage(player);
            
            // Admin commands
            case "reload" -> {
                if (player.hasPermission("plantation.admin")) {
                    reloadPlugin(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                }
            }
            case "reset" -> {
                if (player.hasPermission("plantation.admin") && args.length > 1) {
                    resetPlayerPlantation(player, args[1]);
                } else if (!player.hasPermission("plantation.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation reset <player>");
                }
            }
            case "debug" -> {
                if (player.hasPermission("plantation.admin")) {
                    debugPlantation(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                }
            }
            case "give" -> {
                if (player.hasPermission("plantation.admin") && args.length >= 4) {
                    giveMaterial(player, args);
                } else if (!player.hasPermission("plantation.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation give <player> <material> <tier> [amount]");
                }
            }
            case "setlevel" -> {
                if (player.hasPermission("plantation.admin") && args.length >= 4) {
                    setFarmLevel(player, args);
                } else if (!player.hasPermission("plantation.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation setlevel <player> <farmtype> <instance> <level>");
                }
            }
            case "addexp" -> {
                if (player.hasPermission("plantation.admin") && args.length >= 4) {
                    addFarmExp(player, args);
                } else if (!player.hasPermission("plantation.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation addexp <player> <farmtype> <instance> <amount>");
                }
            }
            case "forceharvest" -> {
                if (player.hasPermission("plantation.admin") && args.length >= 3) {
                    forceHarvest(player, args);
                } else if (!player.hasPermission("plantation.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation forceharvest <farmtype> <instance>");
                }
            }
            case "hardreset" -> {
                if (player.hasPermission("plantation.admin")) {
                    hardReset(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                }
            }
            case "top", "leaderboard" -> showLeaderboard(player);
            case "testmaterials", "checkmaterials" -> {
                if (player.hasPermission("plantation.admin")) {
                    testMaterialDetection(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                }
            }
            case "hologram", "holo" -> {
                if (player.hasPermission("plantation.admin")) {
                    if (args.length > 1) {
                        manageHolograms(player, Arrays.copyOfRange(args, 1, args.length));
                    } else {
                        sendHologramHelp(player);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                }
            }
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void teleportToPlantation(Player player) {
        // Check cooldown
        if (teleportCooldowns.containsKey(player.getUniqueId())) {
            long lastTeleport = teleportCooldowns.get(player.getUniqueId());
            long cooldown = 5000; // 5 seconds
            
            if (System.currentTimeMillis() - lastTeleport < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - lastTeleport)) / 1000;
                player.sendMessage(ChatColor.RED + "Please wait " + remaining + " seconds before teleporting again!");
                return;
            }
        }

        PlantationAreaManager.PlantationArea area = plugin.getPlantationAreaManager().getOrCreateArea(player);
        Location plantationLocation = area.getSpawnPoint();
        
        // Add particles at departure
        player.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL,
            player.getLocation(),
            50, 0.5, 1, 0.5, 0.1
        );
        
        player.teleport(plantationLocation);
        player.sendMessage(ChatColor.GREEN + "✔ Welcome to your plantation!");

// Schedule a nearby hologram-like armor stand cleanup at player location (20-block radius)
        Bukkit.getScheduler().runTask(plugin, () -> {
            String cmd = String.format(
                    "execute as %s at %s run kill @e[type=minecraft:armor_stand,distance=..20,nbt={Invisible:1b,NoGravity:1b,CustomNameVisible:1b}]",
                    player.getName(),
                    player.getName()
            );
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        });

        // Effects at arrival
        player.playSound(plantationLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(
            org.bukkit.Particle.VILLAGER_HAPPY,
            plantationLocation,
            30, 1, 1, 1, 0.1
        );

        // Load player data if not loaded
        plugin.getPlantationManager().loadPlayerData(player.getUniqueId());
        plugin.getOfflineGrowthManager().onPlayerJoin(player.getUniqueId());
        
        // Update cooldown
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Show tips for new players
        if (plugin.getPlantationManager().getPlayerFarms(player.getUniqueId()).isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + "💡 TIP: Right-click on the Sweet Berry Bush to start your first farm!");
                player.sendMessage(ChatColor.YELLOW + "💡 Each block represents a different farm type you can unlock!");
            }, 40L);
        }
    }

    private void showPlantationInfo(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "YOUR PLANTATION INFO" + ChatColor.GOLD + "         ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════╝");
        
        if (farms.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any farms yet!");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/plantation" + ChatColor.GRAY + " to visit your plantation.");
            return;
        }

        // Group farms by type
        Map<FarmType, List<FarmInstance>> farmsByType = farms.stream()
            .collect(Collectors.groupingBy(FarmInstance::getFarmType));

        for (FarmType farmType : FarmType.values()) {
            List<FarmInstance> typeFarms = farmsByType.get(farmType);
            
            if (typeFarms != null && !typeFarms.isEmpty()) {
                player.sendMessage(ChatColor.GREEN + "▸ " + farmType.getDisplayName() + 
                                 ChatColor.GRAY + " (" + typeFarms.size() + "/" + farmType.getMaxInstances() + ")");
                
                for (FarmInstance farm : typeFarms) {
                    String status = farm.isReadyForHarvest() ? ChatColor.GREEN + "READY" : 
                                  ChatColor.YELLOW + formatTime(farm.getTimeUntilNextHarvest());
                    String storage = farm.getTotalStoredItems() >= farm.getMaxStorage() ? 
                                   ChatColor.RED + "FULL" : 
                                   ChatColor.WHITE + "" + farm.getTotalStoredItems() + "/" + farm.getMaxStorage();
                    
                    player.sendMessage(ChatColor.GRAY + "  #" + farm.getInstanceId() + 
                                     " Lv." + farm.getLevel() + 
                                     " | " + status + 
                                     ChatColor.GRAY + " | Storage: " + storage);
                }
            } else if (farmType == FarmType.BERRY_ORCHARDS || 
                      plugin.getDatabaseManager().isFarmUnlocked(player.getUniqueId(), farmType.getId())) {
                player.sendMessage(ChatColor.YELLOW + "▸ " + farmType.getDisplayName() + 
                                 ChatColor.DARK_GRAY + " (0/" + farmType.getMaxInstances() + ") - Available");
            } else {
                player.sendMessage(ChatColor.RED + "▸ " + farmType.getDisplayName() + 
                                 ChatColor.DARK_GRAY + " - LOCKED");
            }
        }
        
        player.sendMessage("");
        double balance = plugin.getEconomyManager().getBalance(player);
        player.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.GREEN + 
                         plugin.getEconomyManager().formatMoney(balance));
    }

    private void showDetailedStats(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║       " + ChatColor.YELLOW + "DETAILED STATISTICS" + ChatColor.GOLD + "       ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════╝");
        
        String sql = "SELECT * FROM farming_player_stats WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                player.sendMessage(ChatColor.YELLOW + "General Stats:");
                player.sendMessage(ChatColor.GRAY + "• Total Farms Created: " + ChatColor.WHITE + 
                                 rs.getInt("total_farms_created"));
                player.sendMessage(ChatColor.GRAY + "• Total Harvests: " + ChatColor.GREEN + 
                                 String.format("%,d", rs.getLong("total_harvests")));
                player.sendMessage(ChatColor.GRAY + "• Materials Collected: " + ChatColor.AQUA + 
                                 String.format("%,d", rs.getLong("total_materials_collected")));
                player.sendMessage(ChatColor.GRAY + "• Money Spent: " + ChatColor.GOLD + 
                                 plugin.getEconomyManager().formatMoney(rs.getDouble("total_money_spent")));
                player.sendMessage(ChatColor.GRAY + "• Money Earned: " + ChatColor.GOLD + 
                                 plugin.getEconomyManager().formatMoney(rs.getDouble("total_money_earned")));
                
                int playMinutes = rs.getInt("play_time_minutes");
                player.sendMessage(ChatColor.GRAY + "• Play Time: " + ChatColor.YELLOW + 
                                 formatPlayTime(playMinutes));
                
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Per-Farm Statistics:");
                
                List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
                for (FarmInstance farm : farms) {
                    player.sendMessage(ChatColor.GREEN + farm.getFarmType().getDisplayName() + " #" + 
                                     farm.getInstanceId() + ":");
                    player.sendMessage(ChatColor.GRAY + "  Harvests: " + farm.getTotalHarvests() + 
                                     " | Produced: " + farm.getTotalMaterialsProduced());
                }
                } else {
                    player.sendMessage(ChatColor.GRAY + "No statistics available yet!");
                }
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Error loading statistics!");
            plugin.getLogger().warning("Failed to load stats for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void listPlayerFarms(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        if (farms.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You don't have any farms!");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "═══ Your Farms (" + farms.size() + ") ═══");
        
        for (FarmInstance farm : farms) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&e" + farm.getFarmType().getDisplayName() + " #" + farm.getInstanceId() + 
                " &7[Lv." + farm.getLevel() + "]" +
                " &f" + farm.getTotalStoredItems() + "/" + farm.getMaxStorage() + 
                (farm.isReadyForHarvest() ? " &a✔ READY" : " &7⏱ " + formatTime(farm.getTimeUntilNextHarvest()))));
        }
    }

    private void openSettingsGUI(Player player) {
        PlayerSettingsGUI settingsGui = new PlayerSettingsGUI(plugin, player);
        player.openInventory(settingsGui.getInventory());
    }

    private void openQuickSellGUI(Player player) {
        QuickSellGUI sellGui = new QuickSellGUI(plugin, player);
        player.openInventory(sellGui.getInventory());
    }

    private void reloadPlugin(Player player) {
        try {
            plugin.reloadConfiguration();
            player.sendMessage(ChatColor.GREEN + "✔ Plugin configuration reloaded successfully!");
            
            // Reload holograms if enabled
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().cleanup();
                plugin.getHologramManager().updateAllHolograms();
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error reloading plugin: " + e.getMessage());
            plugin.getLogger().warning("Reload error: " + e.getMessage());
        }
    }

    private void resetPlayerPlantation(Player player, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        UUID targetUuid;
        
        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            // Try to get offline player
            targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        }

        try {
            // Clear from memory
            plugin.getPlantationManager().getPlayerFarms(targetUuid).clear();
            
            // Clear from database
            String[] tables = {"farming_player_plantations", "farming_plantation_storage", "farming_farm_anchors", 
                              "farming_player_materials", "farming_player_stats", "farming_farm_unlocks"};
            
            for (String table : tables) {
                String sql = "DELETE FROM " + table + " WHERE uuid = ?";
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, targetUuid.toString());
                    stmt.executeUpdate();
                }
            }
            
            player.sendMessage(ChatColor.GREEN + "✔ Plantation reset for " + targetName + " completed!");
            
            if (target != null && target.isOnline()) {
                target.sendMessage(ChatColor.YELLOW + "Your plantation has been reset by an administrator!");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error resetting plantation: " + e.getMessage());
            plugin.getLogger().warning("Reset error: " + e.getMessage());
        }
    }

    private void debugPlantation(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        player.sendMessage(ChatColor.GOLD + "═══ Debug Information ═══");
        player.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "Total farms: " + ChatColor.WHITE + farms.size());
        player.sendMessage(ChatColor.GRAY + "Economy enabled: " + ChatColor.WHITE + 
                         plugin.getEconomyManager().isEconomyEnabled());
        player.sendMessage(ChatColor.GRAY + "Hologram manager: " + ChatColor.WHITE + 
                         (plugin.getHologramManager() != null ? "Active" : "Inactive"));
        
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Farm Details:");
        
        for (FarmInstance farm : farms) {
            player.sendMessage(ChatColor.GREEN + farm.getFarmType().getDisplayName() + 
                             " #" + farm.getInstanceId());
            player.sendMessage(ChatColor.GRAY + "  Level: " + farm.getLevel() + 
                             ", Efficiency: " + farm.getEfficiency() + 
                             ", Exp: " + farm.getExp());
            player.sendMessage(ChatColor.GRAY + "  Ready: " + farm.isReadyForHarvest() + 
                             ", Storage: " + farm.getTotalStoredItems() + "/" + 
                             farm.getMaxStorage());
            player.sendMessage(ChatColor.GRAY + "  Upgrades: S" + farm.getStorageUpgradeLevel() + 
                             " Sp" + farm.getSpeedUpgradeLevel() + 
                             " Q" + farm.getQualityUpgradeLevel());
            player.sendMessage(ChatColor.GRAY + "  Location: " + 
                             (farm.getLocation() != null ? 
                              farm.getLocation().getBlockX() + "," + 
                              farm.getLocation().getBlockY() + "," + 
                              farm.getLocation().getBlockZ() : "null"));
        }
    }

    private void giveMaterial(Player player, String[] args) {
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        try {
            String materialId = args[2].toLowerCase();
            int tier = Integer.parseInt(args[3]);
            int amount = args.length > 4 ? Integer.parseInt(args[4]) : 1;

            MaterialType materialType = MaterialType.fromId(materialId);
            
            if (materialType == null) {
                player.sendMessage(ChatColor.RED + "Invalid material type! Available types:");
                for (MaterialType type : MaterialType.values()) {
                    player.sendMessage(ChatColor.GRAY + "- " + type.getId());
                }
                return;
            }

            if (tier < 1 || tier > 3) {
                player.sendMessage(ChatColor.RED + "Tier must be between 1 and 3!");
                return;
            }

            ItemStack materialItem = plugin.getMaterialManager().createMaterial(materialType, tier, amount);
            
            HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(materialItem);
            
            if (!overflow.isEmpty()) {
                for (ItemStack item : overflow.values()) {
                    target.getWorld().dropItem(target.getLocation(), item);
                }
                player.sendMessage(ChatColor.YELLOW + "Some items were dropped as inventory was full!");
            }
            
            // Update database
            plugin.getDatabaseManager().updatePlayerMaterial(target.getUniqueId(), materialId, tier, amount);
            
            player.sendMessage(ChatColor.GREEN + "✔ Gave " + amount + "x " + 
                             materialType.getDisplayName() + " Tier " + tier + " to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "✔ You received " + amount + "x " + 
                             materialType.getDisplayName() + " Tier " + tier);

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number format!");
        }
    }

    private void setFarmLevel(Player player, String[] args) {
        try {
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found!");
                return;
            }
            
            FarmType farmType = FarmType.fromId(args[2].toLowerCase());
            if (farmType == null) {
                player.sendMessage(ChatColor.RED + "Invalid farm type!");
                return;
            }
            
            int instanceId = Integer.parseInt(args[3]);
            int level = Integer.parseInt(args[4]);
            
            if (level < 1 || level > 10) {
                player.sendMessage(ChatColor.RED + "Level must be between 1 and 10!");
                return;
            }
            
            FarmInstance farm = plugin.getPlantationManager()
                .getFarmInstance(target.getUniqueId(), farmType, instanceId);
            
            if (farm == null) {
                player.sendMessage(ChatColor.RED + "Farm not found!");
                return;
            }
            
            farm.setLevel(level);
            plugin.getPlantationManager().savePlayerData(target.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "✔ Set " + farmType.getDisplayName() + 
                             " #" + instanceId + " to level " + level);
            
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().updateHologram(farm);
            }
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
        }
    }

    private void addFarmExp(Player player, String[] args) {
        try {
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found!");
                return;
            }
            
            FarmType farmType = FarmType.fromId(args[2].toLowerCase());
            if (farmType == null) {
                player.sendMessage(ChatColor.RED + "Invalid farm type!");
                return;
            }
            
            int instanceId = Integer.parseInt(args[3]);
            int exp = Integer.parseInt(args[4]);
            
            FarmInstance farm = plugin.getPlantationManager()
                .getFarmInstance(target.getUniqueId(), farmType, instanceId);
            
            if (farm == null) {
                player.sendMessage(ChatColor.RED + "Farm not found!");
                return;
            }
            
            farm.addExp(exp);
            plugin.getPlantationManager().savePlayerData(target.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "✔ Added " + exp + " exp to " + 
                             farmType.getDisplayName() + " #" + instanceId);
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
        }
    }

    private void forceHarvest(Player player, String[] args) {
        try {
            FarmType farmType = FarmType.fromId(args[1].toLowerCase());
            if (farmType == null) {
                player.sendMessage(ChatColor.RED + "Invalid farm type!");
                return;
            }
            
            int instanceId = Integer.parseInt(args[2]);
            
            FarmInstance farm = plugin.getPlantationManager()
                .getFarmInstance(player.getUniqueId(), farmType, instanceId);
            
            if (farm == null) {
                player.sendMessage(ChatColor.RED + "Farm not found!");
                return;
            }
            
            // Force harvest
            farm.setLastHarvest(0);
            plugin.getPlantationManager().processFarmHarvest(farm);
            plugin.getPlantationManager().savePlayerData(player.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "✔ Forced harvest on " + 
                             farmType.getDisplayName() + " #" + instanceId);
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
        }
    }

    private void showLeaderboard(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║        " + ChatColor.YELLOW + "TOP FARMERS" + ChatColor.GOLD + "              ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════╝");
        
        String sql = "SELECT uuid, total_materials_collected FROM farming_player_stats " +
                    "ORDER BY total_materials_collected DESC LIMIT 10";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int position = 1;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                long materials = rs.getLong("total_materials_collected");
                
                String prefix = position <= 3 ? 
                    (position == 1 ? ChatColor.GOLD + "🥇" : 
                     position == 2 ? ChatColor.GRAY + "🥈" : 
                     ChatColor.YELLOW + "🥉") : ChatColor.WHITE + "#" + position;
                
                player.sendMessage(prefix + " " + ChatColor.WHITE + name + 
                                 ChatColor.GRAY + " - " + ChatColor.GREEN + 
                                 String.format("%,d", materials) + " materials");
                position++;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Error loading leaderboard!");
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║     " + ChatColor.YELLOW + "PLANTATION COMMANDS" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════╝");
        
        player.sendMessage(ChatColor.YELLOW + "Player Commands:");
        player.sendMessage(ChatColor.GREEN + "/plantation" + ChatColor.GRAY + " - Teleport to your plantation");
        player.sendMessage(ChatColor.GREEN + "/plantation info" + ChatColor.GRAY + " - View your plantation info");
        player.sendMessage(ChatColor.GREEN + "/plantation stats" + ChatColor.GRAY + " - View detailed statistics");
        player.sendMessage(ChatColor.GREEN + "/plantation list" + ChatColor.GRAY + " - List all your farms");
        player.sendMessage(ChatColor.GREEN + "/plantation settings" + ChatColor.GRAY + " - Open settings menu");
        player.sendMessage(ChatColor.GREEN + "/plantation top" + ChatColor.GRAY + " - View leaderboard");
        
        if (player.hasPermission("plantation.admin")) {
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Admin Commands:");
            player.sendMessage(ChatColor.RED + "/plantation reload" + ChatColor.GRAY + " - Reload configuration");
            player.sendMessage(ChatColor.RED + "/plantation reset <player>" + ChatColor.GRAY + " - Reset player's plantation");
            player.sendMessage(ChatColor.RED + "/plantation debug" + ChatColor.GRAY + " - Debug information");
            player.sendMessage(ChatColor.RED + "/plantation give <player> <material> <tier> [amount]" + ChatColor.GRAY + " - Give materials");
            player.sendMessage(ChatColor.RED + "/plantation setlevel <player> <farm> <instance> <level>" + ChatColor.GRAY + " - Set farm level");
            player.sendMessage(ChatColor.RED + "/plantation addexp <player> <farm> <instance> <exp>" + ChatColor.GRAY + " - Add experience");
            player.sendMessage(ChatColor.RED + "/plantation forceharvest <farm> <instance>" + ChatColor.GRAY + " - Force harvest");
            player.sendMessage(ChatColor.RED + "/plantation hardreset" + ChatColor.GRAY + " - Clear ALL plugin databases");
        }
    }

    private void hardReset(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⚠ WARNING: This will delete ALL farming plugin data and regenerate all plantations!");
        performHardReset(player);
    }
    
    private void performHardReset(Player player) {
        try {
            player.sendMessage(ChatColor.YELLOW + "Starting hard reset...");
            
            // Clear all in-memory data first
            plugin.getPlantationManager().clearAllData();
            
            // Clear all database tables
            String[] tables = {
                "farming_player_plantations",
                "farming_plantation_storage", 
                "farming_farm_anchors",
                "farming_player_materials",
                "farming_player_stats",
                "farming_farm_unlocks"
            };
            
            for (String table : tables) {
                try {
                    String sql = "DELETE FROM " + table;
                    try (Connection conn = plugin.getDatabaseManager().getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {
                        int deleted = stmt.executeUpdate();
                        player.sendMessage(ChatColor.GRAY + "Cleared " + table + " (" + deleted + " records)");
                    }
                } catch (SQLException e) {
                    player.sendMessage(ChatColor.RED + "Failed to clear " + table + ": " + e.getMessage());
                }
            }
            
            // Clear holograms if enabled
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().cleanup();
                player.sendMessage(ChatColor.GRAY + "Cleared all holograms");
            }
            
            // Regenerate all plantation areas
            player.sendMessage(ChatColor.YELLOW + "Regenerating plantation areas...");
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                plugin.getPlantationAreaManager().regeneratePlayerArea(onlinePlayer);
                player.sendMessage(ChatColor.GRAY + "Regenerated plantation for " + onlinePlayer.getName());
            }
            
            // Also clear any offline player areas that might exist
            plugin.getPlantationAreaManager().clearAllAreas();
            
            player.sendMessage(ChatColor.GREEN + "✔ Hard reset completed successfully!");
            player.sendMessage(ChatColor.YELLOW + "All farming plugin data has been cleared and plantations regenerated.");
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error during hard reset: " + e.getMessage());
            plugin.getLogger().severe("Hard reset error: " + e.getMessage());
        }
    }

    // === HOLOGRAM ADMIN COMMANDS ===
    private void manageHolograms(Player player, String[] args) {
        if (args.length == 0) {
            sendHologramHelp(player);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "refresh", "reload" -> {
                if (plugin.getHologramManager() == null) {
                    player.sendMessage(ChatColor.RED + "Hologram system is disabled!");
                    return;
                }
                plugin.getHologramManager().forceRefreshAll();
                player.sendMessage(ChatColor.GREEN + "✔ All holograms have been refreshed!");
            }
            case "cleanup", "clean" -> {
                if (plugin.getHologramManager() == null) {
                    player.sendMessage(ChatColor.RED + "Hologram system is disabled!");
                    return;
                }
                cleanupAllArmorStands(player);
            }
            case "debug", "info" -> {
                showHologramDebugInfo(player);
            }
            case "test" -> {
                if (plugin.getHologramManager() == null) {
                    player.sendMessage(ChatColor.RED + "Hologram system is disabled!");
                    return;
                }
                testHologram(player);
            }
            case "offset" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation hologram offset <value>");
                    return;
                }
                try {
                    double offset = Double.parseDouble(args[1]);
                    plugin.getConfig().set("plantations.holograms.y_offset", offset);
                    plugin.saveConfig();
                    
                    // Cleanup current manager to allow new offset on reload
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().cleanup();
                    }
                    player.sendMessage(ChatColor.GREEN + "✔ Y-offset set to " + offset + ". Restart or reload to apply.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number format!");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().removePlayerHolograms(player.getUniqueId());
                        player.sendMessage(ChatColor.GREEN + "✔ Your holograms have been removed!");
                    }
                } else {
                    String targetName = args[1];
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null && plugin.getHologramManager() != null) {
                        plugin.getHologramManager().removePlayerHolograms(target.getUniqueId());
                        player.sendMessage(ChatColor.GREEN + "✔ Removed holograms for " + target.getName());
                    } else {
                        player.sendMessage(ChatColor.RED + "Player not found or hologram system disabled!");
                    }
                }
            }
            case "update" -> {
                if (plugin.getHologramManager() == null) {
                    player.sendMessage(ChatColor.RED + "Hologram system is disabled!");
                    return;
                }
                List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
                for (FarmInstance farm : farms) {
                    if (farm.getLocation() != null) {
                        plugin.getHologramManager().updateHologram(farm, true);
                    }
                }
                player.sendMessage(ChatColor.GREEN + "✔ Your farm holograms have been updated!");
            }
            default -> {
                sendHologramHelp(player);
            }
        }
    }

    private void cleanupAllArmorStands(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Scanning for armor stands...");
        
        int totalRemoved = 0;
        Map<String, Integer> removedByWorld = new HashMap<>();
        
        for (World world : Bukkit.getWorlds()) {
            int worldRemoved = 0;
            
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.isMarker() && stand.isSmall() && !stand.isVisible() && 
                    stand.isCustomNameVisible() && stand.getCustomName() != null) {
                    
                    String name = stand.getCustomName();
                    if (name.contains("Orchards") || name.contains("Groves") || 
                        name.contains("Caverns") || name.contains("Patches") ||
                        name.contains("Gardens") || name.contains("Mangroves") ||
                        name.contains("Sanctuaries") || name.contains("READY") ||
                        name.contains("Next:") || name.contains("Storage:") ||
                        name.contains("Efficiency:") || name.contains("Quality") ||
                        name.contains("Speed")) {
                        
                        stand.remove();
                        worldRemoved++;
                        totalRemoved++;
                    }
                }
            }
            
            if (worldRemoved > 0) {
                removedByWorld.put(world.getName(), worldRemoved);
            }
        }
        
        player.sendMessage(ChatColor.GREEN + "✔ Cleanup complete!");
        player.sendMessage(ChatColor.GRAY + "Total removed: " + ChatColor.WHITE + totalRemoved);
        
        for (Map.Entry<String, Integer> entry : removedByWorld.entrySet()) {
            player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }
    }

    private void showHologramDebugInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Hologram Debug Info ===");
        
        boolean enabled = plugin.getConfig().getBoolean("plantations.holograms.enabled", true);
        player.sendMessage(ChatColor.YELLOW + "System Status:");
        player.sendMessage(ChatColor.GRAY + "  Enabled: " + (enabled ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        
        if (plugin.getHologramManager() != null) {
            player.sendMessage(ChatColor.GRAY + "  Manager: " + ChatColor.GREEN + "ACTIVE");
        } else {
            player.sendMessage(ChatColor.GRAY + "  Manager: " + ChatColor.RED + "INACTIVE");
        }
        
        player.sendMessage(ChatColor.YELLOW + "Configuration:");
        double yOffset = plugin.getConfig().getDouble("plantations.holograms.y_offset", 1.5);
        player.sendMessage(ChatColor.GRAY + "  Y-Offset: " + ChatColor.WHITE + yOffset);
        
        player.sendMessage(ChatColor.YELLOW + "Your Settings:");
        boolean playerEnabled = plugin.getDatabaseManager()
            .getPlayerBooleanSetting(player.getUniqueId(), "hologram_enabled", true);
        player.sendMessage(ChatColor.GRAY + "  Holograms Enabled: " + (playerEnabled ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        
        player.sendMessage(ChatColor.YELLOW + "Your Farm Holograms:");
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        if (farms.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  No farms found");
        } else {
            for (FarmInstance farm : farms) {
                boolean hasHolo = plugin.getHologramManager() != null && plugin.getHologramManager().hasHologram(farm);
                player.sendMessage(ChatColor.GRAY + "  " + farm.getFarmType().getDisplayName() + " #" + farm.getInstanceId() + ": " + (hasHolo ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "MISSING"));
                if (farm.getLocation() != null) {
                    Location loc = farm.getLocation();
                    player.sendMessage(ChatColor.DARK_GRAY + "    Location: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                }
            }
        }
        
        player.sendMessage(ChatColor.YELLOW + "Nearby Armor Stands (10 block radius):");
        int nearbyCount = 0;
        int hologramCount = 0;
        
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof ArmorStand stand) {
                nearbyCount++;
                if (stand.isMarker() && stand.isSmall() && !stand.isVisible() && 
                    stand.isCustomNameVisible() && stand.getCustomName() != null) {
                    hologramCount++;
                }
            }
        }
        
        player.sendMessage(ChatColor.GRAY + "  Total: " + ChatColor.WHITE + nearbyCount);
        player.sendMessage(ChatColor.GRAY + "  Holograms: " + ChatColor.WHITE + hologramCount);
    }

    private void testHologram(Player player) {
        Location testLoc = player.getLocation().add(0, 2, 3);
        
        player.sendMessage(ChatColor.YELLOW + "Creating test hologram at your location...");
        
        plugin.getHologramManager().showFloatingText(
            testLoc, 
            "&a&lTEST HOLOGRAM\n&7If you see this,\n&7holograms are working!", 
            5000
        );
        
        player.sendMessage(ChatColor.GREEN + "✔ Test hologram created for 5 seconds!");
        player.sendMessage(ChatColor.GRAY + "Look 3 blocks in front of you, 2 blocks up.");
    }

    private void sendHologramHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Hologram Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/plantation hologram <subcommand>");
        player.sendMessage(ChatColor.GREEN + "  refresh" + ChatColor.GRAY + " - Refresh all holograms");
        player.sendMessage(ChatColor.GREEN + "  cleanup" + ChatColor.GRAY + " - Remove all hologram armor stands");
        player.sendMessage(ChatColor.GREEN + "  debug" + ChatColor.GRAY + " - Show debug information");
        player.sendMessage(ChatColor.GREEN + "  test" + ChatColor.GRAY + " - Create a test hologram");
        player.sendMessage(ChatColor.GREEN + "  offset <value>" + ChatColor.GRAY + " - Set Y-offset for holograms");
        player.sendMessage(ChatColor.GREEN + "  remove [player]" + ChatColor.GRAY + " - Remove holograms");
        player.sendMessage(ChatColor.GREEN + "  update" + ChatColor.GRAY + " - Update your farm holograms");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player player)) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("tp", "info", "stats", "list", "settings", "help", "top", "hologram", "holo");
            
            if (player.hasPermission("plantation.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.addAll(Arrays.asList("reload", "reset", "debug", "give", "setlevel", "addexp", "forceharvest", "hardreset", "testmaterials"));
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("reset") || subCommand.equals("give") || subCommand.equals("setlevel") || subCommand.equals("addexp")) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (subCommand.equals("forceharvest")) {
                for (FarmType type : FarmType.values()) {
                    if (type.getId().startsWith(args[1].toLowerCase())) {
                        completions.add(type.getId());
                    }
                }
            } else if (subCommand.equals("hologram") || subCommand.equals("holo")) {
                completions.addAll(Arrays.asList("refresh", "cleanup", "debug", "test", "offset", "remove", "update"));
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("give")) {
                for (MaterialType type : MaterialType.values()) {
                    if (type.getId().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(type.getId());
                    }
                }
            } else if (subCommand.equals("setlevel") || subCommand.equals("addexp")) {
                for (FarmType type : FarmType.values()) {
                    if (type.getId().startsWith(args[2].toLowerCase())) {
                        completions.add(type.getId());
                    }
                }
            } else if (subCommand.equals("forceharvest")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "6"));
            }
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("give")) {
                completions.addAll(Arrays.asList("1", "2", "3"));
            } else if (subCommand.equals("setlevel") || subCommand.equals("addexp")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "6"));
            }
        } else if (args.length == 5) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("give")) {
                completions.addAll(Arrays.asList("1", "10", "32", "64"));
            } else if (subCommand.equals("setlevel")) {
                for (int i = 1; i <= 10; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if (subCommand.equals("addexp")) {
                completions.addAll(Arrays.asList("10", "50", "100", "500", "1000"));
            }
        }

        return completions;
    }

    private String formatTime(long millis) {
        long hours = millis / (1000L * 60 * 60);
        long minutes = (millis / (1000L * 60)) % 60;
        long seconds = (millis / 1000L) % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
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

    /**
     * Test material detection for debugging integration issues
     */
    private void testMaterialDetection(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Material Detection Test ===");
        player.sendMessage(ChatColor.YELLOW + "Checking your inventory and pouch...");
        player.sendMessage(ChatColor.GRAY + "Pouch integration: " + 
                          (plugin.getPouchIntegrationManager().isEnabled() ? "✅ ENABLED" : "❌ DISABLED"));
        
        // Test each material type
        for (MaterialType type : MaterialType.values()) {
            player.sendMessage(ChatColor.AQUA + type.getDisplayName() + " (" + type.getId() + "):");
            
            int totalInv = 0;
            int totalPouch = 0;
            
            // Check all tiers
            for (int tier = 1; tier <= 3; tier++) {
                // Check inventory
                int invCount = plugin.getMaterialManager().countMaterialInInventory(player, type, tier);
                if (invCount > 0) {
                    player.sendMessage(ChatColor.GREEN + "  Inventory T" + tier + ": " + invCount);
                    totalInv += invCount;
                }
                
                // Check pouch using the new public method
                if (plugin.getPouchIntegrationManager().isEnabled()) {
                    int pouchCount = plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), type, tier);
                    if (pouchCount > 0) {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "  Pouch T" + tier + ": " + pouchCount);
                        totalPouch += pouchCount;
                    }
                    
                    // Show the pouch key being used
                    String pouchKey = plugin.getPouchIntegrationManager().getPouchItemKey(type, tier);
                    player.sendMessage(ChatColor.GRAY + "    (Key: " + pouchKey + ")");
                }
            }
            
            if (totalInv > 0 || totalPouch > 0) {
                player.sendMessage(ChatColor.GOLD + "  📊 Total: " + (totalInv + totalPouch) + 
                                 " (Inv: " + totalInv + ", Pouch: " + totalPouch + ")");
            } else {
                player.sendMessage(ChatColor.GRAY + "  None found");
            }
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Test Complete ===");
        
        // Test specific item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand != null && itemInHand.getType() != org.bukkit.Material.AIR) {
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "🔍 Item in hand analysis:");
            
            if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName()) {
                String displayName = itemInHand.getItemMeta().getDisplayName();
                String stripped = ChatColor.stripColor(displayName);
                
                player.sendMessage(ChatColor.GRAY + "Display: " + displayName);
                player.sendMessage(ChatColor.GRAY + "Stripped: " + stripped);
                
                if (plugin.getMaterialManager().isFarmingMaterial(itemInHand)) {
                    MaterialType type = plugin.getMaterialManager().getMaterialType(itemInHand);
                    int tier = plugin.getMaterialManager().getMaterialTier(itemInHand);
                    player.sendMessage(ChatColor.GREEN + "✅ Detected as: " + type.getDisplayName() + " Tier " + tier);
                    
                    // Show the pouch key this would map to
                    if (plugin.getPouchIntegrationManager().isEnabled()) {
                        String pouchKey = plugin.getPouchIntegrationManager().getPouchItemKey(type, tier);
                        player.sendMessage(ChatColor.AQUA + "🔑 Pouch key: " + pouchKey);
                        
                        // Check how many are in pouch
                        int pouchAmount = plugin.getPouchIntegrationManager().getItemQuantityInPouch(player.getUniqueId(), type, tier);
                        player.sendMessage(ChatColor.AQUA + "💰 In pouch: " + pouchAmount);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Not detected as farming material");
                    
                    // Try to identify pattern
                    if (stripped.matches("\\[\\s*[IVX]+\\s*\\].*")) {
                        player.sendMessage(ChatColor.YELLOW + "⚠️ Matches tier pattern but not recognized");
                    }
                }
            } else {
                player.sendMessage(ChatColor.GRAY + "No display name or meta");
            }
        }
        
        // Test pouch integration status
        if (plugin.getPouchIntegrationManager().isEnabled()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "🔗 Pouch integration is working!");
            player.sendMessage(ChatColor.GRAY + "API object: " + (plugin.getPouchIntegrationManager().getPouchAPI() != null ? "✅" : "❌"));
        } else {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Pouch integration is disabled!");
            player.sendMessage(ChatColor.GRAY + "Check if IngredientPouchPlugin is loaded and enabled.");
        }
    }
}