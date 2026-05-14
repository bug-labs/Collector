package xyz.bugsum.collector.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.utils.LocationUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory cache of every Collector in loaded chunks. Keyed by serialized Location. */
public class CollectorManager {

    private final Collector plugin;
    private final ConcurrentHashMap<String, CollectorData> collectors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> chunkIndex = new ConcurrentHashMap<>();

    public CollectorManager(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                hydrateChunk(c);
            }
        }
    }

    public void shutdown() {
        collectors.clear();
        chunkIndex.clear();
    }

    public void hydrateChunk(Chunk chunk) {
        DatabaseManager db = plugin.getPluginManager().getDatabase();
        for (CollectorData d : db.loadCollectorsInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            putCollector(d);
        }
    }

    public void unloadChunk(Chunk chunk) {
        long key = LocationUtil.chunkKey(chunk.getX(), chunk.getZ());
        Set<String> ids = chunkIndex.remove(key);
        if (ids == null) return;
        // Flush any dirty collectors before evicting them from the cache.
        for (String id : ids) {
            CollectorData c = collectors.remove(id);
            if (c != null && c.isDirty()) {
                plugin.getPluginManager().getDatabase().enqueueSave(c);
            }
        }
    }

    public void putCollector(CollectorData d) {
        String key = LocationUtil.serialize(d.getLocation());
        collectors.put(key, d);
        chunkIndex.computeIfAbsent(LocationUtil.chunkKey(d.getLocation()),
            k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void removeCollector(Location loc) {
        String key = LocationUtil.serialize(loc);
        collectors.remove(key);
        Set<String> ids = chunkIndex.get(LocationUtil.chunkKey(loc));
        if (ids != null) ids.remove(key);
    }

    public CollectorData getCollector(Location loc) {
        return collectors.get(LocationUtil.serialize(loc));
    }

    public Collection<CollectorData> allLoadedCollectors() {
        return Collections.unmodifiableCollection(collectors.values());
    }

    /** True if any Collector exists in the same chunk as the given location (cache-only). */
    public boolean hasCollectorInChunk(Location loc) {
        Set<String> ids = chunkIndex.get(LocationUtil.chunkKey(loc));
        return ids != null && !ids.isEmpty();
    }
}
