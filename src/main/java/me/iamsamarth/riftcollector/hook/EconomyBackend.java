package me.iamsamarth.riftcollector.hook;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * One economy plugin. A currency is named by a plain string, so a price in the config can say
 * which currency it is in and the backend that knows that name handles it.
 */
public interface EconomyBackend {

    /** The name used in config under economy.providers. */
    String id();

    boolean available();

    /** Whether this backend knows the named currency. A blank name means its default one. */
    boolean handles(String currency);

    /** Charges an online player. Buying always happens from a menu, so the payer is present. */
    boolean withdraw(Player payer, String currency, double amount);

    /** Pays a player who may well be offline, since collectors keep working either way. */
    void deposit(OfflinePlayer target, String currency, double amount);

    String format(String currency, double amount);

    /** Currency names this backend offers, for the console summary on startup. */
    Collection<String> currencies();
}
