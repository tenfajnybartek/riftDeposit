package pl.tenfajnybartek.riftdeposit.data;

import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.deposit.DepositData;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MySQL-backed storage using HikariCP.
 *
 * <p>Table schema:
 * <pre>
 * deposit_data (
 *   uuid     VARCHAR(36)  NOT NULL,
 *   item_key VARCHAR(64)  NOT NULL,
 *   amount   INT UNSIGNED NOT NULL DEFAULT 0,
 *   PRIMARY KEY (uuid, item_key)
 * )
 * </pre>
 * </p>
 */
public final class MySQLStorageProvider implements StorageProvider {

    private static final String TABLE = "deposit_data";

    private final HikariConnectionPool pool;
    private final Logger log;
    private final ConfigManager config;
    private ExecutorService executor;

    public MySQLStorageProvider(ConfigManager config, Logger log) {
        this.config = config;
        this.log = log;
        this.pool = new HikariConnectionPool();
    }

    @Override
    public void init() throws Exception {
        pool.init(config);
        executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "RiftDeposit-MySQL");
            t.setDaemon(true);
            return t;
        });
        createTable();
        log.info("[Storage] Using MySQL backend (" + config.getMysqlHost() + "/" + config.getMysqlDatabase() + ")");
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` ("
                + "`uuid`     VARCHAR(36)  NOT NULL,"
                + "`item_key` VARCHAR(64)  NOT NULL,"
                + "`amount`   INT UNSIGNED NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (`uuid`, `item_key`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (Connection conn = pool.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public CompletableFuture<DepositData> load(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            DepositData data = new DepositData(playerUuid);
            String sql = "SELECT `item_key`, `amount` FROM `" + TABLE + "` WHERE `uuid` = ?";

            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("item_key");
                        int amount = rs.getInt("amount");
                        if (amount > 0) data.setStored(key, amount);
                    }
                }
            } catch (SQLException e) {
                log.severe("[MySQL] Failed to load data for " + playerUuid + ": " + e.getMessage());
            }
            return data;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(DepositData data) {
        // Snapshot on calling thread (main thread) BEFORE going async
        final UUID uuid = data.getPlayerUuid();
        final java.util.Map<String, Integer> snapshot = data.snapshot();

        return CompletableFuture.runAsync(() -> {
            String deleteSql = "DELETE FROM `" + TABLE + "` WHERE `uuid` = ?";
            String insertSql = "INSERT INTO `" + TABLE + "` (`uuid`, `item_key`, `amount`) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`)";

            try (Connection conn = pool.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Delete rows that are now 0 / removed
                    try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                        del.setString(1, uuid.toString());
                        del.executeUpdate();
                    }

                    // Insert current non-zero values
                    if (!snapshot.isEmpty()) {
                        try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                            for (var entry : snapshot.entrySet()) {
                                if (entry.getValue() > 0) {
                                    ins.setString(1, uuid.toString());
                                    ins.setString(2, entry.getKey());
                                    ins.setInt(3, entry.getValue());
                                    ins.addBatch();
                                }
                            }
                            ins.executeBatch();
                        }
                    }

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.severe("[MySQL] Failed to save data for " + uuid + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM `" + TABLE + "` WHERE `uuid` = ?";
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.severe("[MySQL] Failed to delete data for " + playerUuid + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        pool.close();
    }
}