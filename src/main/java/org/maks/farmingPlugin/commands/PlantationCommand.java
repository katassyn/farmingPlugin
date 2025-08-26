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
import org.bukkit.inventory.ItemStack;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;
import org.maks.farmingPlugin.gui.PlantationGUI;
import org.maks.farmingPlugin.gui.PlayerSettingsGUI;
import org.maks.farmingPlugin.materials.MaterialType;
import org.maks.farmingPlugin.managers.PlantationAreaManager;

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

        if (!player.hasPermission("plantation.access")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use plantation commands!");
            return true;
        }

        if (args.length == 0) {
            teleportToPlantation(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "tp", "teleport", "visit" -> teleportToPlantation(player);
            case "info" -> showPlantationInfo(player);
            case "stats", "statistics" -> showDetailedStats(player);
            case "list" -> listPlayerFarms(player);
            case "settings" -> openSettingsGUI(player);
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
            case "top", "leaderboard" -> showLeaderboard(player);
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
        
        try {
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";
            var stmt = plugin.getDatabaseManager().prepareStatement(sql);
            stmt.setString(1, player.getUniqueId().toString());
            var rs = stmt.executeQuery();
            
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
            
            rs.close();
            stmt.close();
        } catch (Exception e) {
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
            String[] tables = {"player_plantations", "plantation_storage", "farm_anchors", 
                              "player_materials", "player_stats", "farm_unlocks"};
            
            for (String table : tables) {
                String sql = "DELETE FROM " + table + " WHERE uuid = ?";
                var stmt = plugin.getDatabaseManager().prepareStatement(sql);
                stmt.setString(1, targetUuid.toString());
                stmt.executeUpdate();
                stmt.close();
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
        
        try {
            String sql = "SELECT uuid, total_materials_collected FROM player_stats " +
                        "ORDER BY total_materials_collected DESC LIMIT 10";
            var stmt = plugin.getDatabaseManager().prepareStatement(sql);
            var rs = stmt.executeQuery();
            
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
            
            rs.close();
            stmt.close();
        } catch (Exception e) {
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
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player player)) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("tp", "info", "stats", "list", "settings", "help", "top");
            
            if (player.hasPermission("plantation.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.addAll(Arrays.asList("reload", "reset", "debug", "give", "setlevel", "addexp", "forceharvest"));
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
}