package org.maks.farmingPlugin.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.maks.farmingPlugin.FarmingPlugin;

import java.util.UUID;

public class EconomyManager {
    private final FarmingPlugin plugin;
    private Economy economy;
    private boolean economyEnabled = false;

    public EconomyManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features disabled.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy plugin found! Economy features disabled.");
            return false;
        }

        economy = rsp.getProvider();
        economyEnabled = economy != null;

        if (economyEnabled) {
            plugin.getLogger().info("Economy integration enabled with " + economy.getName());
        } else {
            plugin.getLogger().warning("Failed to setup economy integration!");
        }

        return economyEnabled;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    public double getBalance(UUID playerId) {
        if (!economyEnabled) return 0;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return 0;
        
        return economy.getBalance(player);
    }

    public double getBalance(Player player) {
        if (!economyEnabled) return 0;
        return economy.getBalance(player);
    }

    public boolean hasBalance(UUID playerId, double amount) {
        if (!economyEnabled) return false;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        
        return economy.has(player, amount);
    }

    public boolean hasBalance(Player player, double amount) {
        if (!economyEnabled) return false;
        return economy.has(player, amount);
    }

    public boolean withdrawMoney(UUID playerId, double amount) {
        if (!economyEnabled) return false;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdrawMoney(Player player, double amount) {
        if (!economyEnabled) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean depositMoney(UUID playerId, double amount) {
        if (!economyEnabled) return false;
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;
        
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean depositMoney(Player player, double amount) {
        if (!economyEnabled) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String formatMoney(double amount) {
        if (!economyEnabled) return String.valueOf(amount);
        return economy.format(amount);
    }

    public String getCurrencyName() {
        if (!economyEnabled) return "Money";
        return economy.currencyNamePlural();
    }

    public Economy getEconomy() {
        return economy;
    }
}