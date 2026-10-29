package me.iamsamarth.riftcollector.hook;

import me.iamsamarth.riftcollector.config.PluginConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every price in this plugin may name the currency it is in. This hands the price to the first
 * economy plugin that knows that currency, so a server can charge upgrades in gems and pay auto
 * sell out in coins without any of the callers knowing which plugin is behind either.
 */
public final class EconomyService {

    private final Plugin plugin;
    private final List<EconomyBackend> backends = new ArrayList<>();

    private String defaultCurrency = "";

    public EconomyService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void hook(PluginConfig.Economy settings) {
        backends.clear();
        defaultCurrency = settings.defaultCurrency();

        List<EconomyBackend> known = List.of(new ExcellentEconomyBackend(plugin), new VaultBackend(plugin));
        for (String id : settings.providers()) {
            known.stream()
                    .filter(backend -> backend.id().equalsIgnoreCase(id))
                    .findFirst()
                    .ifPresentOrElse(backends::add, () -> plugin.getLogger()
                            .warning("Unknown economy provider '" + id + "' in economy.providers."));
        }

        if (backends.stream().noneMatch(EconomyBackend::available)) {
            plugin.getLogger().info("No economy plugin found; upgrades and selling are unavailable.");
        }
    }

    /** Whether anything at all can take money. */
    public boolean available() {
        return backends.stream().anyMatch(EconomyBackend::available);
    }

    /** Whether this particular currency can be paid in. */
    public boolean available(@Nullable String currency) {
        return backendFor(currency) != null;
    }

    public boolean withdraw(Player payer, @Nullable String currency, double amount) {
        EconomyBackend backend = backendFor(currency);
        return backend != null && backend.withdraw(payer, resolve(currency), amount);
    }

    public void deposit(OfflinePlayer target, @Nullable String currency, double amount) {
        EconomyBackend backend = backendFor(currency);
        if (backend == null) {
            plugin.getLogger().warning("Nothing can pay out " + amount + " in currency '"
                    + resolve(currency) + "'; check economy.providers.");
            return;
        }
        backend.deposit(target, resolve(currency), amount);
    }

    public String format(@Nullable String currency, double amount) {
        EconomyBackend backend = backendFor(currency);
        String formatted = backend == null ? null : backend.format(resolve(currency), amount);
        return formatted != null ? formatted : String.format(Locale.ROOT, "%,.2f", amount);
    }

    /** Formats in the currency used when a price does not name one. */
    public String format(double amount) {
        return format(null, amount);
    }

    public String defaultCurrency() {
        return defaultCurrency;
    }

    public Set<String> currencies() {
        Set<String> names = new LinkedHashSet<>();
        for (EconomyBackend backend : backends) {
            if (backend.available()) {
                names.addAll(backend.currencies());
            }
        }
        return names;
    }

    private String resolve(@Nullable String currency) {
        return currency == null || currency.isBlank() ? defaultCurrency : currency;
    }

    private @Nullable EconomyBackend backendFor(@Nullable String currency) {
        String wanted = resolve(currency);
        for (EconomyBackend backend : backends) {
            if (backend.available() && backend.handles(wanted)) {
                return backend;
            }
        }
        return null;
    }
}
