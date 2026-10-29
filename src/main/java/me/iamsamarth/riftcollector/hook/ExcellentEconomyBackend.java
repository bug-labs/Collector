package me.iamsamarth.riftcollector.hook;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * ExcellentEconomy, which is where more than one currency comes from: every currency it defines
 * can be named in this plugin's config, so upgrades and payouts need not use the same money.
 *
 * <p>It is reached by reflection rather than compiled against. Its jar ships Java 25 bytecode,
 * which a Java 21 toolchain cannot read, so a compile dependency would force this plugin's build
 * onto a newer JDK for one optional hook. The plugin registers its API with Bukkit's services
 * manager, so the object itself is found without reflection; only the calls are reflective, and
 * if any of them is missing the hook reports that once and stays out of the way.
 */
public final class ExcellentEconomyBackend implements EconomyBackend {

    private static final String API_CLASS = "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI";

    private final Plugin plugin;

    private @Nullable Object api;
    private Method hasCurrency;
    private Method getBalance;
    private Method withdraw;
    private Method deposit;
    private Method depositAsync;
    private Method getCurrency;
    private Method getCurrencies;
    private Method currencyId;
    private Method currencyFormat;

    public ExcellentEconomyBackend(Plugin plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        Object service = findService();
        if (service == null) {
            return;
        }
        try {
            Class<?> type = service.getClass();
            hasCurrency = type.getMethod("hasCurrency", String.class);
            getBalance = type.getMethod("getBalance", Player.class, String.class);
            withdraw = type.getMethod("withdraw", Player.class, String.class, double.class);
            deposit = type.getMethod("deposit", Player.class, String.class, double.class);
            depositAsync = type.getMethod("depositAsync", UUID.class, String.class, double.class);
            getCurrency = type.getMethod("getCurrency", String.class);
            getCurrencies = type.getMethod("getCurrencies");
            api = service;
            plugin.getLogger().info("ExcellentEconomy currencies: " + String.join(", ", currencies()) + ".");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Found ExcellentEconomy but its API does not look"
                    + " the way this version expects; its currencies are unavailable.", exception);
        }
    }

    /** The API is registered with the services manager, so it is found by service type name. */
    private @Nullable Object findService() {
        for (Class<?> service : plugin.getServer().getServicesManager().getKnownServices()) {
            if (!service.getName().equals(API_CLASS)) {
                continue;
            }
            RegisteredServiceProvider<?> registration =
                    plugin.getServer().getServicesManager().getRegistration(service);
            if (registration != null) {
                return registration.getProvider();
            }
        }
        return null;
    }

    @Override
    public String id() {
        return "EXCELLENT_ECONOMY";
    }

    @Override
    public boolean available() {
        return api != null;
    }

    @Override
    public boolean handles(String currency) {
        if (api == null || currency == null || currency.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(call(hasCurrency, currency));
    }

    @Override
    public boolean withdraw(Player payer, String currency, double amount) {
        if (api == null || amount <= 0) {
            return false;
        }
        Object balance = call(getBalance, payer, currency);
        if (!(balance instanceof Number held) || held.doubleValue() < amount) {
            return false;
        }
        return Boolean.TRUE.equals(call(withdraw, payer, currency, amount));
    }

    @Override
    public void deposit(OfflinePlayer target, String currency, double amount) {
        if (api == null || amount <= 0) {
            return;
        }
        Player online = target.getPlayer();
        if (online != null) {
            call(deposit, online, currency, amount);
            return;
        }

        // Offline balances are only reachable asynchronously, which suits a payout: nothing here
        // waits on the result, and a player who cannot be loaded is reported rather than ignored.
        Object pending = call(depositAsync, target.getUniqueId(), currency, amount);
        if (pending instanceof CompletableFuture<?> future) {
            future.thenAccept(result -> {
                if (result == null || result.toString().contains("FAILURE")) {
                    plugin.getLogger().warning("ExcellentEconomy could not pay " + amount + " "
                            + currency + " to " + target.getUniqueId() + " (" + result + ").");
                }
            });
        }
    }

    @Override
    public @Nullable String format(String currency, double amount) {
        if (api == null) {
            return null;
        }
        Object found = call(getCurrency, currency);
        if (found == null) {
            return null;
        }
        try {
            if (currencyFormat == null) {
                currencyFormat = found.getClass().getMethod("format", double.class);
            }
            Object formatted = currencyFormat.invoke(found, amount);
            return formatted instanceof String text ? text : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public Collection<String> currencies() {
        if (api == null) {
            return List.of();
        }
        Object all = call(getCurrencies);
        if (!(all instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object currency : iterable) {
            try {
                if (currencyId == null) {
                    currencyId = currency.getClass().getMethod("getId");
                }
                if (currencyId.invoke(currency) instanceof String id) {
                    ids.add(id);
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                return ids;
            }
        }
        return ids;
    }

    private @Nullable Object call(@Nullable Method method, Object... arguments) {
        if (api == null || method == null) {
            return null;
        }
        try {
            return method.invoke(api, arguments);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING,
                    "ExcellentEconomy call " + method.getName() + " failed.", exception);
            return null;
        }
    }
}
