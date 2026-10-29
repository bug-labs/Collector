package me.iamsamarth.riftcollector.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Money through whichever economy plugin registered itself with Vault. Vault only ever exposes
 * one currency, so this backend answers to the blank currency name and to "vault".
 */
public final class VaultBackend implements EconomyBackend {

    private final Plugin plugin;
    private @Nullable Economy economy;

    public VaultBackend(Plugin plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("Vault is installed but no economy plugin registered with it.");
            return;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("Vault economy: " + economy.getName() + ".");
    }

    @Override
    public String id() {
        return "VAULT";
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public boolean handles(String currency) {
        return available() && (currency == null || currency.isBlank() || currency.equalsIgnoreCase("vault"));
    }

    @Override
    public boolean withdraw(Player payer, String currency, double amount) {
        if (economy == null || amount <= 0) {
            return false;
        }
        return economy.has(payer, amount) && economy.withdrawPlayer(payer, amount).transactionSuccess();
    }

    @Override
    public void deposit(OfflinePlayer target, String currency, double amount) {
        if (economy != null && amount > 0) {
            economy.depositPlayer(target, amount);
        }
    }

    @Override
    public String format(String currency, double amount) {
        return economy == null ? null : economy.format(amount);
    }

    @Override
    public Collection<String> currencies() {
        return available() ? List.of("vault") : List.of();
    }
}
