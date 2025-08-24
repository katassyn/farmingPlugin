package org.maks.farmingPlugin.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;
import org.maks.farmingPlugin.farms.FarmInstance;
import org.maks.farmingPlugin.farms.FarmType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PlantationCommand implements CommandExecutor, TabCompleter {
    private final FarmingPlugin plugin;

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
            case "tp", "teleport" -> teleportToPlantation(player);
            case "info" -> showPlantationInfo(player);
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
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /plantation give <player> <material> <tier> [amount]");
                }
            }
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void teleportToPlantation(Player player) {
        Location plantationLocation = getPlantationLocation(player.getUniqueId());
        
        if (plantationLocation != null) {
            player.teleport(plantationLocation);
            player.sendMessage(ChatColor.GREEN + "Welcome to your plantation!");
            
            plugin.getPlantationManager().loadPlayerData(player.getUniqueId());
            plugin.getOfflineGrowthManager().onPlayerJoin(player.getUniqueId());
        } else {
            createPlantationForPlayer(player);
        }
    }

    private void showPlantationInfo(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        player.sendMessage(ChatColor.GOLD + "=== Your Plantation Info ===");
        
        if (farms.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any farms yet!");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/plantation" + ChatColor.GRAY + " to visit your plantation.");
            return;
        }

        for (FarmType farmType : FarmType.values()) {
            long count = farms.stream()
                    .filter(farm -> farm.getFarmType() == farmType)
                    .count();
            
            if (count > 0) {
                player.sendMessage(ChatColor.GREEN + "• " + farmType.getDisplayName() + 
                                 ChatColor.GRAY + ": " + ChatColor.WHITE + count + "/" + farmType.getMaxInstances());
            } else if (farmType == FarmType.BERRY_ORCHARDS) {
                player.sendMessage(ChatColor.YELLOW + "• " + farmType.getDisplayName() + 
                                 ChatColor.GRAY + ": " + ChatColor.RED + "Available to unlock");
            }
        }
        
        double balance = plugin.getEconomyManager().getBalance(player);
        String formattedBalance = plugin.getEconomyManager().formatMoney(balance);
        player.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.GREEN + formattedBalance);
    }

    private void reloadPlugin(Player player) {
        try {
            plugin.reloadConfig();
            player.sendMessage(ChatColor.GREEN + "Plugin configuration reloaded successfully!");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error reloading plugin: " + e.getMessage());
        }
    }

    private void resetPlayerPlantation(Player player, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        UUID targetUuid = target.getUniqueId();
        
        try {
            player.sendMessage(ChatColor.GREEN + "Plantation reset for " + target.getName() + " completed!");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error resetting plantation: " + e.getMessage());
        }
    }

    private void debugPlantation(Player player) {
        List<FarmInstance> farms = plugin.getPlantationManager().getPlayerFarms(player.getUniqueId());
        
        player.sendMessage(ChatColor.GOLD + "=== Debug Information ===");
        player.sendMessage(ChatColor.GRAY + "Total farms: " + ChatColor.WHITE + farms.size());
        
        for (FarmInstance farm : farms) {
            player.sendMessage(ChatColor.GREEN + "Farm: " + farm.getFarmType().getDisplayName() + 
                             " #" + farm.getInstanceId());
            player.sendMessage(ChatColor.GRAY + "  Level: " + farm.getLevel() + 
                             ", Efficiency: " + farm.getEfficiency());
            player.sendMessage(ChatColor.GRAY + "  Ready: " + farm.isReadyForHarvest() + 
                             ", Storage: " + farm.getTotalStoredItems() + "/" + 
                             farm.getFarmType().getStorageLimit());
        }
    }

    private void giveMaterial(Player player, String[] args) {
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        try {
            String materialId = args[2];
            int tier = Integer.parseInt(args[3]);
            int amount = args.length > 4 ? Integer.parseInt(args[4]) : 1;

            org.maks.farmingPlugin.materials.MaterialType materialType = 
                org.maks.farmingPlugin.materials.MaterialType.fromId(materialId);
            
            if (materialType == null) {
                player.sendMessage(ChatColor.RED + "Invalid material type!");
                return;
            }

            if (tier < 1 || tier > 3) {
                player.sendMessage(ChatColor.RED + "Tier must be between 1 and 3!");
                return;
            }

            org.bukkit.inventory.ItemStack materialItem = 
                plugin.getMaterialManager().createMaterial(materialType, tier, amount);
            
            target.getInventory().addItem(materialItem);
            
            player.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + 
                             materialType.getDisplayName() + " Tier " + tier + " to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You received " + amount + "x " + 
                             materialType.getDisplayName() + " Tier " + tier);

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number format!");
        }
    }

    private Location getPlantationLocation(UUID playerId) {
        World world = plugin.getServer().getWorld("world");
        if (world == null) return null;
        
        String worldName = playerId.toString().substring(0, 8);
        int x = playerId.hashCode() % 10000;
        int z = (playerId.hashCode() / 10000) % 10000;
        
        return new Location(world, x, 100, z);
    }

    private void createPlantationForPlayer(Player player) {
        Location plantationLocation = getPlantationLocation(player.getUniqueId());
        
        if (plantationLocation != null) {
            player.teleport(plantationLocation);
            player.sendMessage(ChatColor.GREEN + "Welcome to your new plantation!");
            player.sendMessage(ChatColor.GRAY + "Right-click blocks to interact with your farms!");
            
            plugin.getPlantationManager().loadPlayerData(player.getUniqueId());
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Plantation Commands ===");
        player.sendMessage(ChatColor.GREEN + "/plantation" + ChatColor.GRAY + " - Teleport to your plantation");
        player.sendMessage(ChatColor.GREEN + "/plantation info" + ChatColor.GRAY + " - View your plantation info");
        
        if (player.hasPermission("plantation.admin")) {
            player.sendMessage(ChatColor.YELLOW + "Admin Commands:");
            player.sendMessage(ChatColor.GREEN + "/plantation reload" + ChatColor.GRAY + " - Reload plugin");
            player.sendMessage(ChatColor.GREEN + "/plantation reset <player>" + ChatColor.GRAY + " - Reset player's plantation");
            player.sendMessage(ChatColor.GREEN + "/plantation debug" + ChatColor.GRAY + " - Debug information");
            player.sendMessage(ChatColor.GREEN + "/plantation give <player> <material> <tier> [amount]" + ChatColor.GRAY + " - Give materials");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("tp", "info", "reload", "reset", "debug", "give");
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("give")) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (org.maks.farmingPlugin.materials.MaterialType materialType : 
                 org.maks.farmingPlugin.materials.MaterialType.values()) {
                String id = materialType.getId();
                if (id.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(id);
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(Arrays.asList("1", "2", "3"));
        }

        return completions;
    }
}