package xyz.bugsum.collector.data;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter @Setter
public class CollectorData {
    private long id;
    private Location location;
    private UUID owner;

    private int tierSpeed = 1;
    private int tierRange = 1;
    private int tierCapacity = 1;

    private boolean autosell = false;
    private boolean autosmelt = false;
    /** Default ON: most users want XP capture without having to dig into a sub-menu first. */
    private boolean xpCollect = true;
    private boolean hologramVisible = true;
    private boolean magnet = false;

    /** Custom name for this Collector — null/empty = use default "Collector". */
    private String displayName;

    /** Co-owners that may open the GUI / withdraw. Hydrated from DB on chunk load. */
    private final Set<UUID> friends = ConcurrentHashMap.newKeySet();

    /** Scheduling: server-tick at which this Collector is next due for a magnet pulse. */
    private volatile long nextMagnetTick = 0;

    /** Virtual XP balance — incremented when XP orbs are absorbed, withdrawn via the XP GUI. */
    private long xpStored = 0;

    private final FilterData filter = new FilterData();

    /** Virtual aggregated storage: material -> amount. Allows huge stacks (e.g. DIAMOND x12345). */
    private final ConcurrentHashMap<Material, Long> storage = new ConcurrentHashMap<>();

    private long itemsCollected = 0;
    private double moneyEarned = 0;

    private long createdAt = System.currentTimeMillis();

    /** Scheduling: server-tick at which this Collector is next due for a scan. */
    private volatile long nextScanTick = 0;

    /** Marks this Collector as needing a DB flush. Cleared by the async writer. */
    private volatile boolean dirty = false;

    public CollectorData(Location loc, UUID owner) {
        this.location = loc;
        this.owner = owner;
    }

    public void markDirty() { this.dirty = true; }

    public Map<Material, Long> getStorageView() { return storage; }

    /** Sum of all currently-stored item counts. Used by the hologram + GUI {@code <items>} placeholder. */
    public long currentItems() {
        long total = 0;
        for (long v : storage.values()) total += v;
        return total;
    }
}
