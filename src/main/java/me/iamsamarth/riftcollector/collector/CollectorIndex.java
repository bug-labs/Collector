package me.iamsamarth.riftcollector.collector;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every known collector, in memory. The database is the stored copy; this is what the sweep,
 * the menus and the per-chunk limit look at.
 */
public final class CollectorIndex {

    public record BlockPos(UUID world, int x, int y, int z) {
        public static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        static BlockPos of(Collector collector) {
            return new BlockPos(collector.world(), collector.x(), collector.y(), collector.z());
        }
    }

    public record ChunkPos(UUID world, int x, int z) {
        public static ChunkPos of(Chunk chunk) {
            return new ChunkPos(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }

        static ChunkPos of(Collector collector) {
            return new ChunkPos(collector.world(), collector.x() >> 4, collector.z() >> 4);
        }
    }

    private final Map<BlockPos, Collector> byBlock = new HashMap<>();
    private final Map<ChunkPos, List<Collector>> byChunk = new HashMap<>();

    public void add(Collector collector) {
        byBlock.put(BlockPos.of(collector), collector);
        byChunk.computeIfAbsent(ChunkPos.of(collector), key -> new ArrayList<>()).add(collector);
    }

    public void remove(Collector collector) {
        byBlock.remove(BlockPos.of(collector));
        ChunkPos chunkPos = ChunkPos.of(collector);
        List<Collector> inChunk = byChunk.get(chunkPos);
        if (inChunk != null && inChunk.remove(collector) && inChunk.isEmpty()) {
            byChunk.remove(chunkPos);
        }
    }

    public @Nullable Collector get(Block block) {
        return byBlock.get(BlockPos.of(block));
    }

    public int countIn(Chunk chunk) {
        return byChunk.getOrDefault(ChunkPos.of(chunk), List.of()).size();
    }

    public Collection<Collector> all() {
        return byBlock.values();
    }

    /** A copy, so callers can remove collectors while walking it. */
    public Map<ChunkPos, List<Collector>> byChunk() {
        Map<ChunkPos, List<Collector>> copy = new HashMap<>(byChunk.size());
        byChunk.forEach((chunkPos, collectors) -> copy.put(chunkPos, List.copyOf(collectors)));
        return copy;
    }

    public void clear() {
        byBlock.clear();
        byChunk.clear();
    }
}
