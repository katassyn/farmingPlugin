package org.maks.farmingPlugin.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.farmingPlugin.FarmingPlugin;

public class FarmerShopCommand implements CommandExecutor {
    private final FarmingPlugin plugin;

    public FarmerShopCommand(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        // NAPRAWKA: Nadaj tymczasową permisję mycraftingplugin.use
        boolean hadPermission = player.hasPermission("mycraftingplugin.use");
        if (!hadPermission) {
            // Temporarily add permission
            player.addAttachment(plugin, "mycraftingplugin.use", true, 20); // 1 second
        }

        try {
            // Execute /farmer_shop command from MyCraftingPlugin2
            player.performCommand("farmer_shop");
            player.sendMessage(ChatColor.GREEN + "Farmer Shop opened!");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Unable to open Farmer Shop. Is MyCraftingPlugin2 loaded?");
            plugin.getLogger().warning("Failed to execute farmer_shop command: " + e.getMessage());
        }

        return true;
    }
}