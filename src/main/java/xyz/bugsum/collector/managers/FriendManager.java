package xyz.bugsum.collector.managers;

import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.data.CollectorData;

import java.util.UUID;

/**
 * Thin facade over {@link DatabaseManager} for managing co-owners of a Collector. The cached
 * set on {@link CollectorData#getFriends()} is the source of truth at runtime; the DB is only
 * touched on changes.
 */
public class FriendManager {

    private final Collector plugin;

    public FriendManager(Collector plugin) {
        this.plugin = plugin;
    }

    public boolean has(CollectorData data, UUID player) {
        if (data == null || player == null) return false;
        return data.getFriends().contains(player);
    }

    /** True if the player owns OR is a friend of this Collector. */
    public boolean canUse(CollectorData data, UUID player) {
        if (data == null || player == null) return false;
        if (data.getOwner() != null && data.getOwner().equals(player)) return true;
        return data.getFriends().contains(player);
    }

    public boolean add(CollectorData data, UUID player) {
        if (data == null || player == null) return false;
        if (!data.getFriends().add(player)) return false;
        plugin.getPluginManager().getDatabase().runAsync(() ->
            plugin.getPluginManager().getDatabase().addFriend(data.getId(), player));
        return true;
    }

    public boolean remove(CollectorData data, UUID player) {
        if (data == null || player == null) return false;
        if (!data.getFriends().remove(player)) return false;
        plugin.getPluginManager().getDatabase().runAsync(() ->
            plugin.getPluginManager().getDatabase().removeFriend(data.getId(), player));
        return true;
    }
}
