package xyz.bugsum.collector.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import xyz.bugsum.collector.Collector;

public class ChunkListener implements Listener {

    private final Collector plugin;

    public ChunkListener(Collector plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        plugin.getPluginManager().getCollector().hydrateChunk(event.getChunk());
        // Sweep stray hologram entities in this chunk that have no live Collector
        // (e.g. server crashed before despawning them).
        if (plugin.getPluginManager().getHolograms() != null) {
            plugin.getPluginManager().getHolograms().cleanupChunk(event.getChunk());
        }
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        plugin.getPluginManager().getCollector().unloadChunk(event.getChunk());
    }
}
