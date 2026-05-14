package xyz.bugsum.collector.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import xyz.bugsum.collector.Collector;

import java.util.UUID;

public class EconomyManager {

    private final Collector plugin;
    private Economy economy;

    public EconomyManager(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!plugin.getPluginManager().getConfigManager().isVaultEnabled()) return;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not detected — economy features disabled.");
            return;
        }
        try {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
            } else {
                plugin.getLogger().warning("Vault present but no Economy provider registered.");
            }
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().info("Vault classes not on classpath — economy disabled.");
        }
    }

    public boolean isAvailable() { return economy != null; }

    public boolean withdraw(UUID player, double amount) {
        if (!isAvailable()) return false;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(player);
        if (economy.getBalance(op) < amount) return false;
        return economy.withdrawPlayer(op, amount).transactionSuccess();
    }

    public boolean deposit(UUID player, double amount) {
        if (!isAvailable() || amount <= 0) return false;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(player);
        return economy.depositPlayer(op, amount).transactionSuccess();
    }

    public double balance(UUID player) {
        if (!isAvailable()) return 0;
        return economy.getBalance(plugin.getServer().getOfflinePlayer(player));
    }
}
