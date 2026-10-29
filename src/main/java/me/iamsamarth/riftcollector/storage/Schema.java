package me.iamsamarth.riftcollector.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates the tables and records which version created them, so later releases can migrate
 * instead of guessing what is already there.
 */
final class Schema {

    static final int VERSION = 2;

    private Schema() {
    }

    static void apply(Database database) throws SQLException {
        String blob = database.sqlite() ? "BLOB" : "MEDIUMBLOB";
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS riftcollector_schema (
                    version INT NOT NULL
                )""",
                """
                CREATE TABLE IF NOT EXISTS collectors (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    world VARCHAR(36) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    owner VARCHAR(36),
                    owner_name VARCHAR(16),
                    material VARCHAR(64) NOT NULL,
                    earnings DOUBLE NOT NULL DEFAULT 0,
                    items_sold BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    CONSTRAINT collector_position UNIQUE (world, x, y, z)
                )""",
                """
                CREATE TABLE IF NOT EXISTS collector_items (
                    collector_id VARCHAR(36) NOT NULL,
                    slot INT NOT NULL,
                    item %s NOT NULL,
                    amount BIGINT NOT NULL,
                    PRIMARY KEY (collector_id, slot),
                    FOREIGN KEY (collector_id) REFERENCES collectors (id) ON DELETE CASCADE
                )""".formatted(blob),
                """
                CREATE TABLE IF NOT EXISTS collector_upgrades (
                    collector_id VARCHAR(36) NOT NULL,
                    type VARCHAR(32) NOT NULL,
                    level INT NOT NULL,
                    PRIMARY KEY (collector_id, type),
                    FOREIGN KEY (collector_id) REFERENCES collectors (id) ON DELETE CASCADE
                )""",
                """
                CREATE TABLE IF NOT EXISTS collector_settings (
                    collector_id VARCHAR(36) NOT NULL,
                    setting VARCHAR(32) NOT NULL,
                    value VARCHAR(64) NOT NULL,
                    PRIMARY KEY (collector_id, setting),
                    FOREIGN KEY (collector_id) REFERENCES collectors (id) ON DELETE CASCADE
                )""",
                """
                CREATE TABLE IF NOT EXISTS collector_filter (
                    collector_id VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    PRIMARY KEY (collector_id, material),
                    FOREIGN KEY (collector_id) REFERENCES collectors (id) ON DELETE CASCADE
                )""",
                """
                CREATE TABLE IF NOT EXISTS collector_trusted (
                    collector_id VARCHAR(36) NOT NULL,
                    player VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16),
                    PRIMARY KEY (collector_id, player),
                    FOREIGN KEY (collector_id) REFERENCES collectors (id) ON DELETE CASCADE
                )"""
        );

        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            // Tables are created if missing above, so a new table alone needs no migration step;
            // the row only records which version last touched this database.
            int stored = readVersion(statement);
            if (stored == 0) {
                statement.executeUpdate("INSERT INTO riftcollector_schema (version) VALUES (" + VERSION + ")");
            } else if (stored != VERSION) {
                statement.executeUpdate("UPDATE riftcollector_schema SET version = " + VERSION);
            }
        }
    }

    private static int readVersion(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT version FROM riftcollector_schema LIMIT 1")) {
            return result.next() ? result.getInt("version") : 0;
        }
    }
}
