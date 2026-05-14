package xyz.bugsum.collector.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.data.FilterData;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQLite persistence with a dirty-set + periodic batched async flush. Saves are queued
 * via {@link #enqueueSave(CollectorData)} and drained on the worker thread.
 */
public class DatabaseManager {

    private final Collector plugin;
    private Connection connection;
    private ExecutorService executor;

    /** Collectors with pending writes. Keyed by id (location-derived once inserted). */
    private final Set<CollectorData> dirty = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DatabaseManager(Collector plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // Paper bundles SQLite-JDBC at runtime — don't shade/relocate.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found on the classpath.");
        }

        File dbFile = new File(plugin.getDataFolder(), plugin.getPluginManager().getConfigManager().getDbFile());
        if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
            }
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialise SQLite database: " + e.getMessage());
            e.printStackTrace();
        }

        int threads = Math.max(1, plugin.getPluginManager().getConfigManager().getDbThreads());
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Collector-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public void shutdown() {
        // Final synchronous flush so we don't lose in-flight writes on /reload or shutdown.
        flushDirty();
        if (executor != null) {
            executor.shutdown();
            try { executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
        }
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS collectors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world TEXT NOT NULL, x INT, y INT, z INT," +
                "owner_uuid TEXT," +
                "tier_speed INT DEFAULT 1, tier_range INT DEFAULT 1, tier_capacity INT DEFAULT 1," +
                "autosell INT DEFAULT 0, autosmelt INT DEFAULT 0," +
                "xp_collect INT DEFAULT 1, hologram_visible INT DEFAULT 1," +
                "magnet INT DEFAULT 0," +
                "xp_stored INTEGER DEFAULT 0," +
                "display_name TEXT," +
                "filter_mode TEXT DEFAULT 'NONE', filters_json TEXT DEFAULT ''," +
                "storage_json TEXT DEFAULT ''," +
                "items_collected INTEGER DEFAULT 0, money_earned REAL DEFAULT 0," +
                "created_at INTEGER," +
                "UNIQUE(world,x,y,z))");
            st.execute("CREATE INDEX IF NOT EXISTS idx_col_chunk ON collectors(world, x, z)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_col_owner ON collectors(owner_uuid)");

            // Friends (co-owners) table
            st.execute("CREATE TABLE IF NOT EXISTS collector_friends (" +
                "collector_id INTEGER NOT NULL," +
                "friend_uuid TEXT NOT NULL," +
                "PRIMARY KEY(collector_id, friend_uuid))");
            st.execute("CREATE INDEX IF NOT EXISTS idx_friend_uuid ON collector_friends(friend_uuid)");

            // Schema migration markers
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");

            // Drop legacy chest table if upgrading from the old schema
            st.execute("DROP TABLE IF EXISTS collector_chests");
        }

        // Idempotent column additions for legacy installs.
        tryAddColumn("xp_collect", "INT DEFAULT 1");
        tryAddColumn("hologram_visible", "INT DEFAULT 1");
        tryAddColumn("magnet", "INT DEFAULT 0");
        tryAddColumn("xp_stored", "INTEGER DEFAULT 0");
        tryAddColumn("display_name", "TEXT");

        applyMigrations();
    }

    private void tryAddColumn(String name, String def) {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE collectors ADD COLUMN " + name + " " + def);
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    /** Run one-shot, marker-guarded data migrations. Each marker runs at most once per DB. */
    private void applyMigrations() {
        // schema_v2_xp_default: legacy installs created xp_collect with DEFAULT 0; flip
        // every existing 0 to 1 once so XP collection works out-of-box like fresh installs.
        runOnce("schema_v2_xp_default", () -> {
            try (Statement st = connection.createStatement()) {
                int n = st.executeUpdate("UPDATE collectors SET xp_collect = 1 WHERE xp_collect = 0");
                if (n > 0) plugin.getLogger().info("Migration v2 (xp_default): updated " + n + " row(s).");
            }
        });
    }

    private void runOnce(String marker, MigrationStep step) {
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM meta WHERE key=?")) {
                ps.setString(1, marker);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            step.run();
            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)")) {
                ps.setString(1, marker);
                ps.setString(2, "1");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Migration " + marker + " failed: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface MigrationStep { void run() throws SQLException; }

    /* ------------- Async helpers ------------- */

    public void runAsync(Runnable r) {
        if (executor == null) Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
        else executor.submit(r);
    }

    /** Mark a Collector for the next batched flush. */
    public void enqueueSave(CollectorData data) {
        if (data == null) return;
        dirty.add(data);
    }

    /** Drain the dirty set and write them in one transaction. Safe to call sync (e.g. on shutdown). */
    public void flushDirty() {
        if (connection == null || dirty.isEmpty()) return;
        // Snapshot + clear so concurrent edits aren't lost (they re-mark dirty).
        Set<CollectorData> snapshot = new HashSet<>(dirty);
        dirty.removeAll(snapshot);
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE collectors SET tier_speed=?, tier_range=?, tier_capacity=?, " +
                "autosell=?, autosmelt=?, xp_collect=?, hologram_visible=?, magnet=?, xp_stored=?, " +
                "display_name=?, filter_mode=?, filters_json=?, storage_json=?, " +
                "items_collected=?, money_earned=? WHERE world=? AND x=? AND y=? AND z=?")) {
                for (CollectorData d : snapshot) {
                    Location l = d.getLocation();
                    ps.setInt(1, d.getTierSpeed());
                    ps.setInt(2, d.getTierRange());
                    ps.setInt(3, d.getTierCapacity());
                    ps.setInt(4, d.isAutosell() ? 1 : 0);
                    ps.setInt(5, d.isAutosmelt() ? 1 : 0);
                    ps.setInt(6, d.isXpCollect() ? 1 : 0);
                    ps.setInt(7, d.isHologramVisible() ? 1 : 0);
                    ps.setInt(8, d.isMagnet() ? 1 : 0);
                    ps.setLong(9, d.getXpStored());
                    ps.setString(10, d.getDisplayName());
                    ps.setString(11, d.getFilter().getMode().name());
                    ps.setString(12, d.getFilter().serializeItems());
                    ps.setString(13, serializeStorage(d.getStorage()));
                    ps.setLong(14, d.getItemsCollected());
                    ps.setDouble(15, d.getMoneyEarned());
                    ps.setString(16, l.getWorld().getName());
                    ps.setInt(17, l.getBlockX());
                    ps.setInt(18, l.getBlockY());
                    ps.setInt(19, l.getBlockZ());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            for (CollectorData d : snapshot) d.setDirty(false);
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().warning("flushDirty failed: " + e.getMessage());
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /* ------------- Collector CRUD ------------- */

    public synchronized long insertCollector(CollectorData d) {
        String sql = "INSERT OR REPLACE INTO collectors " +
            "(world,x,y,z,owner_uuid,tier_speed,tier_range,tier_capacity," +
            "autosell,autosmelt,xp_collect,hologram_visible,magnet,xp_stored," +
            "display_name,filter_mode,filters_json,storage_json," +
            "items_collected,money_earned,created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Location l = d.getLocation();
            ps.setString(1, l.getWorld().getName());
            ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY());
            ps.setInt(4, l.getBlockZ());
            ps.setString(5, d.getOwner() != null ? d.getOwner().toString() : null);
            ps.setInt(6, d.getTierSpeed());
            ps.setInt(7, d.getTierRange());
            ps.setInt(8, d.getTierCapacity());
            ps.setInt(9, d.isAutosell() ? 1 : 0);
            ps.setInt(10, d.isAutosmelt() ? 1 : 0);
            ps.setInt(11, d.isXpCollect() ? 1 : 0);
            ps.setInt(12, d.isHologramVisible() ? 1 : 0);
            ps.setInt(13, d.isMagnet() ? 1 : 0);
            ps.setLong(14, d.getXpStored());
            ps.setString(15, d.getDisplayName());
            ps.setString(16, d.getFilter().getMode().name());
            ps.setString(17, d.getFilter().serializeItems());
            ps.setString(18, serializeStorage(d.getStorage()));
            ps.setLong(19, d.getItemsCollected());
            ps.setDouble(20, d.getMoneyEarned());
            ps.setLong(21, d.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    d.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("insertCollector failed: " + e.getMessage());
        }
        return -1;
    }

    public synchronized void deleteCollector(Location l) {
        try (PreparedStatement ps = connection.prepareStatement(
            "DELETE FROM collectors WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, l.getWorld().getName());
            ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY());
            ps.setInt(4, l.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("deleteCollector failed: " + e.getMessage());
        }
    }

    public synchronized List<CollectorData> loadCollectorsInChunk(World world, int chunkX, int chunkZ) {
        List<CollectorData> list = new ArrayList<>();
        if (connection == null) return list;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM collectors WHERE world=? AND x>=? AND x<? AND z>=? AND z<?")) {
            ps.setString(1, world.getName());
            ps.setInt(2, chunkX << 4);
            ps.setInt(3, (chunkX + 1) << 4);
            ps.setInt(4, chunkZ << 4);
            ps.setInt(5, (chunkZ + 1) << 4);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readCollector(rs, world));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadCollectorsInChunk failed: " + e.getMessage());
        }
        return list;
    }

    public synchronized boolean chunkHasCollector(World world, int chunkX, int chunkZ) {
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT 1 FROM collectors WHERE world=? AND x>=? AND x<? AND z>=? AND z<? LIMIT 1")) {
            ps.setString(1, world.getName());
            ps.setInt(2, chunkX << 4);
            ps.setInt(3, (chunkX + 1) << 4);
            ps.setInt(4, chunkZ << 4);
            ps.setInt(5, (chunkZ + 1) << 4);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("chunkHasCollector failed: " + e.getMessage());
        }
        return false;
    }

    /* ------------- Friends ------------- */

    public synchronized java.util.Set<UUID> loadFriends(long collectorId) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        if (connection == null || collectorId <= 0) return out;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT friend_uuid FROM collector_friends WHERE collector_id=?")) {
            ps.setLong(1, collectorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { out.add(UUID.fromString(rs.getString(1))); } catch (Exception ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadFriends failed: " + e.getMessage());
        }
        return out;
    }

    public synchronized boolean addFriend(long collectorId, UUID friend) {
        if (connection == null || collectorId <= 0 || friend == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT OR IGNORE INTO collector_friends(collector_id,friend_uuid) VALUES(?,?)")) {
            ps.setLong(1, collectorId);
            ps.setString(2, friend.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("addFriend failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean removeFriend(long collectorId, UUID friend) {
        if (connection == null || collectorId <= 0 || friend == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
            "DELETE FROM collector_friends WHERE collector_id=? AND friend_uuid=?")) {
            ps.setLong(1, collectorId);
            ps.setString(2, friend.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("removeFriend failed: " + e.getMessage());
            return false;
        }
    }

    /* ------------- Aggregate stats (PAPI) ------------- */

    public synchronized OwnerStats aggregateOwnerStats(UUID owner) {
        OwnerStats s = new OwnerStats();
        if (connection == null || owner == null) return s;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT COUNT(*), COALESCE(SUM(items_collected),0), COALESCE(SUM(money_earned),0)," +
            " COALESCE(SUM(xp_stored),0), COALESCE(MAX(tier_speed),0)," +
            " COALESCE(MAX(tier_range),0), COALESCE(MAX(tier_capacity),0)" +
            " FROM collectors WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.count       = rs.getInt(1);
                    s.totalItems  = rs.getLong(2);
                    s.totalMoney  = rs.getDouble(3);
                    s.totalXp     = rs.getLong(4);
                    s.maxSpeed    = rs.getInt(5);
                    s.maxRange    = rs.getInt(6);
                    s.maxCapacity = rs.getInt(7);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("aggregateOwnerStats failed: " + e.getMessage());
        }
        return s;
    }

    public static final class OwnerStats {
        public int count;
        public long totalItems;
        public double totalMoney;
        public long totalXp;
        public int maxSpeed, maxRange, maxCapacity;
    }

    public synchronized int countCollectorsByOwner(UUID owner) {
        if (connection == null) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT COUNT(*) FROM collectors WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("countCollectorsByOwner failed: " + e.getMessage());
        }
        return 0;
    }

    private CollectorData readCollector(ResultSet rs, World world) throws SQLException {
        Location loc = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
        UUID owner = null;
        String ownerStr = rs.getString("owner_uuid");
        if (ownerStr != null) {
            try { owner = UUID.fromString(ownerStr); } catch (IllegalArgumentException ignored) {}
        }
        CollectorData d = new CollectorData(loc, owner);
        d.setId(rs.getLong("id"));
        d.setTierSpeed(rs.getInt("tier_speed"));
        d.setTierRange(rs.getInt("tier_range"));
        d.setTierCapacity(rs.getInt("tier_capacity"));
        d.setAutosell(rs.getInt("autosell") != 0);
        d.setAutosmelt(rs.getInt("autosmelt") != 0);
        try { d.setXpCollect(rs.getInt("xp_collect") != 0); } catch (SQLException ignored) {}
        try { d.setHologramVisible(rs.getInt("hologram_visible") != 0); } catch (SQLException ignored) {}
        try { d.setMagnet(rs.getInt("magnet") != 0); } catch (SQLException ignored) {}
        try { d.setXpStored(rs.getLong("xp_stored")); } catch (SQLException ignored) {}
        try { d.setDisplayName(rs.getString("display_name")); } catch (SQLException ignored) {}
        // Friends are stored in a side-table; eagerly load them so the per-tick listener
        // path stays allocation-free.
        d.getFriends().clear();
        d.getFriends().addAll(loadFriends(d.getId()));
        try {
            d.getFilter().setMode(FilterData.Mode.valueOf(rs.getString("filter_mode")));
        } catch (IllegalArgumentException | NullPointerException ignored) {}
        d.getFilter().deserializeItems(rs.getString("filters_json"));
        deserializeStorage(rs.getString("storage_json"), d);
        d.setItemsCollected(rs.getLong("items_collected"));
        d.setMoneyEarned(rs.getDouble("money_earned"));
        d.setCreatedAt(rs.getLong("created_at"));
        return d;
    }

    /* ------------- Storage codec ------------- */

    /** Compact CSV codec to avoid pulling in a JSON library: "MAT:N,MAT:N,...". */
    private static String serializeStorage(Map<Material, Long> storage) {
        if (storage == null || storage.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(storage.size() * 16);
        boolean first = true;
        for (Map.Entry<Material, Long> e : storage.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static void deserializeStorage(String s, CollectorData out) {
        out.getStorage().clear();
        if (s == null || s.isEmpty()) return;
        for (String token : s.split(",")) {
            int colon = token.indexOf(':');
            if (colon <= 0) continue;
            Material m = Material.matchMaterial(token.substring(0, colon));
            if (m == null) continue;
            try { out.getStorage().put(m, Long.parseLong(token.substring(colon + 1))); }
            catch (NumberFormatException ignored) {}
        }
    }

    /** Used by /collector wipe etc. */
    @SuppressWarnings("unused")
    public synchronized int countAll(Collection<CollectorData> mem) { return mem.size(); }
}
