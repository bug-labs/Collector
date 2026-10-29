package me.iamsamarth.riftcollector.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.iamsamarth.riftcollector.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/** The connection pool behind every query; SQLite and MySQL differ only in how it is set up. */
public final class Database implements AutoCloseable {

    private final PluginConfig.DatabaseType type;
    private final HikariDataSource dataSource;

    public Database(PluginConfig.Database settings, File dataFolder) {
        this.type = settings.type();

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("RiftCollector");
        if (type == PluginConfig.DatabaseType.SQLITE) {
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath());
            // SQLite serialises writes anyway, and foreign keys are off per connection by default.
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
        } else {
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + settings.host() + ":" + settings.port() + "/"
                    + settings.database() + "?useSSL=" + settings.useSsl()
                    + "&characterEncoding=utf8&rewriteBatchedStatements=true");
            hikari.setUsername(settings.username());
            hikari.setPassword(settings.password());
            hikari.setMaximumPoolSize(settings.poolSize());
        }
        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean sqlite() {
        return type == PluginConfig.DatabaseType.SQLITE;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
