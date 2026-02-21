package pl.tenfajnybartek.riftdeposit.data;

import org.bukkit.configuration.file.YamlConfiguration;
import pl.tenfajnybartek.riftdeposit.deposit.DepositData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * YAML-backed storage.
 * Each player has their own file: {@code plugins/RiftDeposit/players/<uuid>.yml}
 *
 * <p>Structure:
 * <pre>
 * stored:
 *   golden_apple: 3
 *   ender_pearl: 2
 * </pre>
 * </p>
 */
public final class YamlStorageProvider implements StorageProvider {

    private final File playersDir;
    private final Logger log;
    private ExecutorService executor;

    public YamlStorageProvider(File dataFolder, Logger log) {
        this.playersDir = new File(dataFolder, "players");
        this.log = log;
    }

    @Override
    public void init() {
        playersDir.mkdirs();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RiftDeposit-YAML-IO");
            t.setDaemon(true);
            return t;
        });
        log.info("[Storage] Using YAML backend (dir: " + playersDir.getPath() + ")");
    }

    @Override
    public CompletableFuture<DepositData> load(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            File file = playerFile(playerUuid);
            DepositData data = new DepositData(playerUuid);

            if (!file.exists()) return data;

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            var section = yaml.getConfigurationSection("stored");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    int amount = section.getInt(key, 0);
                    if (amount > 0) data.setStored(key, amount);
                }
            }
            return data;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(DepositData data) {
        return CompletableFuture.runAsync(() -> {
            File file = playerFile(data.getPlayerUuid());
            YamlConfiguration yaml = new YamlConfiguration();

            Map<String, Integer> stored = data.snapshot(); // thread-safe detached copy
            for (Map.Entry<String, Integer> entry : stored.entrySet()) {
                if (entry.getValue() > 0) {
                    yaml.set("stored." + entry.getKey(), entry.getValue());
                }
            }

            // If nothing to store, delete the file to keep the directory clean
            if (stored.isEmpty()) {
                if (file.exists()) file.delete();
                return;
            }

            try {
                yaml.save(file);
            } catch (IOException e) {
                log.severe("[Storage] Failed to save data for " + data.getPlayerUuid() + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            File file = playerFile(playerUuid);
            if (file.exists()) file.delete();
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
    }

    private File playerFile(UUID uuid) {
        return new File(playersDir, uuid + ".yml");
    }
}