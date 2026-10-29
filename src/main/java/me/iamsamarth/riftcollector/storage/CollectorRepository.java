package me.iamsamarth.riftcollector.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads collectors at startup and writes changes back on its own thread. Callers hand over
 * finished {@link CollectorRecord}s, so nothing touches Bukkit objects off the main thread.
 */
public final class CollectorRepository {

    private final Database database;
    private final Logger logger;
    private final ExecutorService writer =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "RiftCollector-DB"));

    public CollectorRepository(Database database, Logger logger) throws SQLException {
        this.database = database;
        this.logger = logger;
        Schema.apply(database);
    }

    public List<CollectorRecord> loadAll() throws SQLException {
        Map<UUID, List<CollectorRecord.SlotRecord>> slots = loadSlots();
        Map<UUID, Map<String, Integer>> upgrades = loadUpgrades();
        Map<UUID, Map<String, String>> settings = loadSettings();
        Map<UUID, Set<String>> filters = loadFilters();
        Map<UUID, Map<String, String>> trusted = loadTrusted();
        List<CollectorRecord> collectors = new ArrayList<>();

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, world, x, y, z, owner, owner_name, material, earnings, items_sold, created_at FROM collectors");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("id"));
                String owner = result.getString("owner");
                collectors.add(new CollectorRecord(
                        id,
                        UUID.fromString(result.getString("world")),
                        result.getInt("x"),
                        result.getInt("y"),
                        result.getInt("z"),
                        owner == null ? null : UUID.fromString(owner),
                        result.getString("owner_name"),
                        result.getString("material"),
                        result.getDouble("earnings"),
                        result.getLong("items_sold"),
                        result.getLong("created_at"),
                        slots.getOrDefault(id, List.of()),
                        upgrades.getOrDefault(id, Map.of()),
                        settings.getOrDefault(id, Map.of()),
                        filters.getOrDefault(id, Set.of()),
                        trusted.getOrDefault(id, Map.of())));
            }
        }
        return collectors;
    }

    public void insert(CollectorRecord record) {
        submit("insert collector", connection -> {
            writeCollector(connection, record, true);
            writeChildren(connection, record);
        });
    }

    public void delete(UUID id) {
        submit("delete collector", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM collectors WHERE id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
            // SQLite only honours ON DELETE CASCADE when the pragma is on, so clean up regardless.
            deleteChildren(connection, id);
        });
    }

    public void save(List<CollectorRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        submit("save collectors", connection -> {
            for (CollectorRecord record : records) {
                writeCollector(connection, record, false);
                deleteChildren(connection, record.id());
                writeChildren(connection, record);
            }
        });
    }

    /** Runs the remaining writes on the calling thread; used while the plugin shuts down. */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for collector data to be written.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<UUID, List<CollectorRecord.SlotRecord>> loadSlots() throws SQLException {
        Map<UUID, List<CollectorRecord.SlotRecord>> slots = new HashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT collector_id, slot, item, amount FROM collector_items");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("collector_id"));
                slots.computeIfAbsent(id, key -> new ArrayList<>()).add(new CollectorRecord.SlotRecord(
                        result.getInt("slot"), result.getBytes("item"), result.getLong("amount")));
            }
        }
        return slots;
    }

    private void writeCollector(Connection connection, CollectorRecord record, boolean insert) throws SQLException {
        String sql = insert
                ? """
                  INSERT INTO collectors (id, world, x, y, z, owner, owner_name, material, earnings, items_sold, created_at)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                : """
                  UPDATE collectors SET world = ?, x = ?, y = ?, z = ?, owner = ?, owner_name = ?,
                      material = ?, earnings = ?, items_sold = ?, created_at = ? WHERE id = ?""";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (insert) {
                statement.setString(index++, record.id().toString());
            }
            statement.setString(index++, record.world().toString());
            statement.setInt(index++, record.x());
            statement.setInt(index++, record.y());
            statement.setInt(index++, record.z());
            statement.setString(index++, record.owner() == null ? null : record.owner().toString());
            statement.setString(index++, record.ownerName());
            statement.setString(index++, record.material());
            statement.setDouble(index++, record.earnings());
            statement.setLong(index++, record.itemsSold());
            statement.setLong(index++, record.createdAt());
            if (!insert) {
                statement.setString(index, record.id().toString());
            }
            statement.executeUpdate();
        }
    }

    private Map<UUID, Map<String, Integer>> loadUpgrades() throws SQLException {
        Map<UUID, Map<String, Integer>> upgrades = new HashMap<>();
        forEachRow("SELECT collector_id, type, level FROM collector_upgrades", result ->
                upgrades.computeIfAbsent(UUID.fromString(result.getString("collector_id")), key -> new HashMap<>())
                        .put(result.getString("type"), result.getInt("level")));
        return upgrades;
    }

    private Map<UUID, Map<String, String>> loadSettings() throws SQLException {
        Map<UUID, Map<String, String>> settings = new HashMap<>();
        forEachRow("SELECT collector_id, setting, value FROM collector_settings", result ->
                settings.computeIfAbsent(UUID.fromString(result.getString("collector_id")), key -> new HashMap<>())
                        .put(result.getString("setting"), result.getString("value")));
        return settings;
    }

    private Map<UUID, Set<String>> loadFilters() throws SQLException {
        Map<UUID, Set<String>> filters = new HashMap<>();
        forEachRow("SELECT collector_id, material FROM collector_filter", result ->
                filters.computeIfAbsent(UUID.fromString(result.getString("collector_id")), key -> new HashSet<>())
                        .add(result.getString("material")));
        return filters;
    }

    private Map<UUID, Map<String, String>> loadTrusted() throws SQLException {
        Map<UUID, Map<String, String>> trusted = new HashMap<>();
        forEachRow("SELECT collector_id, player, player_name FROM collector_trusted", result ->
                trusted.computeIfAbsent(UUID.fromString(result.getString("collector_id")), key -> new HashMap<>())
                        .put(result.getString("player"), result.getString("player_name")));
        return trusted;
    }

    private void forEachRow(String sql, RowReader reader) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                reader.read(result);
            }
        }
    }

    private void writeChildren(Connection connection, CollectorRecord record) throws SQLException {
        String id = record.id().toString();

        if (!record.slots().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO collector_items (collector_id, slot, item, amount) VALUES (?, ?, ?, ?)")) {
                for (CollectorRecord.SlotRecord slot : record.slots()) {
                    statement.setString(1, id);
                    statement.setInt(2, slot.slot());
                    statement.setBytes(3, slot.item());
                    statement.setLong(4, slot.amount());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        if (!record.upgrades().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO collector_upgrades (collector_id, type, level) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Integer> upgrade : record.upgrades().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, upgrade.getKey());
                    statement.setInt(3, upgrade.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        if (!record.settings().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO collector_settings (collector_id, setting, value) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> setting : record.settings().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, setting.getKey());
                    statement.setString(3, setting.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        if (!record.filter().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO collector_filter (collector_id, material) VALUES (?, ?)")) {
                for (String material : record.filter()) {
                    statement.setString(1, id);
                    statement.setString(2, material);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        if (!record.trusted().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO collector_trusted (collector_id, player, player_name) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> entry : record.trusted().entrySet()) {
                    statement.setString(1, id);
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private void deleteChildren(Connection connection, UUID id) throws SQLException {
        for (String table : List.of("collector_items", "collector_upgrades", "collector_settings",
                "collector_filter", "collector_trusted")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE collector_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        }
    }

    private interface RowReader {
        void read(ResultSet result) throws SQLException;
    }

    private void submit(String description, SqlTask task) {
        if (writer.isShutdown()) {
            run(description, task);
            return;
        }
        writer.execute(() -> run(description, task));
    }

    private void run(String description, SqlTask task) {
        try (Connection connection = database.connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                task.run(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to " + description, exception);
        }
    }

    private interface SqlTask {
        void run(Connection connection) throws SQLException;
    }
}
