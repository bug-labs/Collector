package xyz.bugsum.collector.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.managers.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PlaceholderAPI expansion. Stats queries hit a per-UUID cache (TTL 5 s) so that PAPI ticks
 * triggered by scoreboards / chat formatters don't hammer SQLite for every refresh.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private static final long TTL_MILLIS = 5_000L;

    private final Collector plugin;
    private final ConcurrentMap<UUID, CachedStats> cache = new ConcurrentHashMap<>();

    public PlaceholderHook(Collector plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "collector"; }
    @Override public @NotNull String getAuthor() { return "Samarth Sharma"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        DatabaseManager.OwnerStats s = stats(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "count"             -> String.valueOf(s.count);
            case "total_items"       -> String.valueOf(s.totalItems);
            case "total_money"       -> String.format("%,.2f", s.totalMoney);
            case "total_money_int"   -> String.format("%,d", (long) s.totalMoney);
            case "total_xp"          -> String.valueOf(s.totalXp);
            case "max_tier_speed"    -> String.valueOf(s.maxSpeed);
            case "max_tier_range"    -> String.valueOf(s.maxRange);
            case "max_tier_capacity" -> String.valueOf(s.maxCapacity);
            default -> null;
        };
    }

    private DatabaseManager.OwnerStats stats(UUID owner) {
        long now = System.currentTimeMillis();
        CachedStats cached = cache.get(owner);
        if (cached != null && now - cached.fetchedAt < TTL_MILLIS) return cached.stats;
        DatabaseManager.OwnerStats fresh = plugin.getPluginManager().getDatabase().aggregateOwnerStats(owner);
        cache.put(owner, new CachedStats(fresh, now));
        return fresh;
    }

    private record CachedStats(DatabaseManager.OwnerStats stats, long fetchedAt) {}
}
