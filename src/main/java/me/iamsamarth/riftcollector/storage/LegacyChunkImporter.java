package me.iamsamarth.riftcollector.storage;

import me.iamsamarth.riftcollector.collector.Collector;
import me.iamsamarth.riftcollector.collector.CollectorService;
import me.iamsamarth.riftcollector.collector.StoredStack;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Earlier versions kept collectors in the persistent data container of their chunk. Those only
 * become readable when the chunk loads, so this listens for chunk loads as well as sweeping the
 * chunks that are already loaded, moves what it finds into the database and clears the chunk data.
 * Owners are left unset: nothing recorded who placed them.
 */
public final class LegacyChunkImporter implements Listener {

    private static final ListPersistentDataType<PersistentDataContainer, PersistentDataContainer> ENTRIES =
            PersistentDataType.LIST.dataContainers();

    private final CollectorService collectors;
    private final Logger logger;
    private final NamespacedKey collectorsKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;
    private final NamespacedKey slotsKey;
    private final NamespacedKey itemsKey;

    public LegacyChunkImporter(Plugin plugin, CollectorService collectors) {
        this.collectors = collectors;
        this.logger = plugin.getLogger();
        this.collectorsKey = new NamespacedKey(plugin, "collectors");
        this.xKey = new NamespacedKey(plugin, "x");
        this.yKey = new NamespacedKey(plugin, "y");
        this.zKey = new NamespacedKey(plugin, "z");
        this.slotsKey = new NamespacedKey(plugin, "slots");
        this.itemsKey = new NamespacedKey(plugin, "items");
    }

    public void importLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                importChunk(chunk);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        importChunk(event.getChunk());
    }

    private void importChunk(Chunk chunk) {
        PersistentDataContainer chunkData = chunk.getPersistentDataContainer();
        List<PersistentDataContainer> entries = chunkData.get(collectorsKey, ENTRIES);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        int imported = 0;
        for (PersistentDataContainer entry : entries) {
            Integer x = entry.get(xKey, PersistentDataType.INTEGER);
            Integer y = entry.get(yKey, PersistentDataType.INTEGER);
            Integer z = entry.get(zKey, PersistentDataType.INTEGER);
            if (x == null || y == null || z == null) {
                continue;
            }
            Block block = chunk.getWorld().getBlockAt(x, y, z);
            if (collectors.get(block) != null) {
                continue;
            }
            Material material = block.getType();
            if (material.isAir()) {
                continue;
            }

            Collector collector = Collector.unowned(chunk.getWorld().getUID(), x, y, z, material);
            restoreItems(entry, collector);
            collectors.adopt(collector);
            imported++;
        }

        chunkData.remove(collectorsKey);
        if (imported > 0) {
            logger.info("Imported " + imported + " collector(s) from chunk data in "
                    + chunk.getWorld().getName() + " at " + chunk.getX() + "," + chunk.getZ() + ".");
        }
    }

    private void restoreItems(PersistentDataContainer entry, Collector collector) {
        int[] indices = entry.get(slotsKey, PersistentDataType.INTEGER_ARRAY);
        byte[] itemBytes = entry.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        if (indices == null || indices.length == 0 || itemBytes == null || itemBytes.length == 0) {
            return;
        }
        // Old storage held plain stacks, so each one becomes a counted stack of its own amount.
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(itemBytes);
        List<StoredStack> contents = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                contents.add(new StoredStack(item, item.getAmount()));
            }
        }
        collector.fill(contents);
    }
}
