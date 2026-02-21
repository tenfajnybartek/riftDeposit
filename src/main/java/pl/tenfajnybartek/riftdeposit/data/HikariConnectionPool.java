package pl.tenfajnybartek.riftdeposit.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thin wrapper around {@link HikariDataSource}.
 * Configured entirely from {@link ConfigManager}.
 */
public final class HikariConnectionPool {

    private HikariDataSource dataSource;

    public void init(ConfigManager config) throws SQLException {
        HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl("jdbc:mysql://"
                + config.getMysqlHost() + ":"
                + config.getMysqlPort() + "/"
                + config.getMysqlDatabase()
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true");
        hikari.setUsername(config.getMysqlUsername());
        hikari.setPassword(config.getMysqlPassword());
        hikari.setMaximumPoolSize(config.getMysqlPoolSize());
        hikari.setConnectionTimeout(config.getMysqlConnectionTimeout());
        hikari.setPoolName("RiftDeposit-Pool");

        // Performance tuning
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(hikari);

        // Validate connection
        try (Connection ignored = dataSource.getConnection()) { /* ok */ }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialised or has been closed.");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean isInitialised() {
        return dataSource != null && !dataSource.isClosed();
    }
}