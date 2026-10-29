package me.iamsamarth.riftcollector.collector;

import me.iamsamarth.riftcollector.storage.CollectorRecord;
import me.iamsamarth.riftcollector.storage.CollectorRepository;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Keeps the in-memory index and the database in step for the whole life of a collector. */
public final class CollectorService {

    private final CollectorIndex index = new CollectorIndex();
    private final CollectorRepository repository;
    private final Logger logger;

    private Consumer<Collector> removalListener = collector -> {
    };

    public CollectorService(CollectorRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public void loadAll() throws SQLException {
        index.clear();
        int skipped = 0;
        for (CollectorRecord record : repository.loadAll()) {
            Collector collector = Collector.restore(record);
            if (collector == null) {
                skipped++;
                continue;
            }
            index.add(collector);
        }
        logger.info("Loaded " + index.all().size() + " collector(s).");
        if (skipped > 0) {
            logger.warning(skipped + " collector(s) were left in the database because their block "
                    + "material no longer exists.");
        }
    }

    public CollectorIndex index() {
        return index;
    }

    public @Nullable Collector get(Block block) {
        return index.get(block);
    }

    public Collector create(Block block, Material material, Player owner) {
        Collector collector = Collector.create(block, material, owner);
        index.add(collector);
        repository.insert(collector.toRecord());
        return collector;
    }

    /** Adopts a collector that was rebuilt from older storage rather than placed by a player. */
    public void adopt(Collector collector) {
        index.add(collector);
        repository.insert(collector.toRecord());
    }

    /** Notified whenever a collector goes away, wherever that happens. */
    public void onRemove(Consumer<Collector> listener) {
        this.removalListener = listener;
    }

    public void remove(Collector collector) {
        index.remove(collector);
        repository.delete(collector.id());
        removalListener.accept(collector);
    }

    public void flush() {
        List<CollectorRecord> changed = new ArrayList<>();
        for (Collector collector : index.all()) {
            if (collector.dirty()) {
                changed.add(collector.toRecord());
                collector.markClean();
            }
        }
        repository.save(changed);
    }

    public void shutdown() {
        flush();
        repository.shutdown();
        index.clear();
    }
}
