package pl.tenfajnybartek.riftdeposit.data;

import pl.tenfajnybartek.riftdeposit.deposit.DepositData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async data persistence contract.
 * Both YAML and MySQL implementations must satisfy this interface.
 *
 * <p>All futures complete on a background thread; callers must
 * switch back to the main thread before touching Bukkit API.</p>
 */
public interface StorageProvider {

    /**
     * Initialise the storage backend (create files, tables, pool, etc.).
     * Called once on plugin enable.
     *
     * @throws Exception if initialisation fails fatally
     */
    void init() throws Exception;

    /**
     * Loads stored deposit data for the given player.
     * Returns a new empty {@link DepositData} if no record exists.
     */
    CompletableFuture<DepositData> load(UUID playerUuid);

    /**
     * Persists the given {@link DepositData}.
     * Empty entries (amount = 0) should be removed / not written.
     */
    CompletableFuture<Void> save(DepositData data);

    /**
     * Completely removes all stored data for the given player.
     */
    CompletableFuture<Void> delete(UUID playerUuid);

    /**
     * Gracefully shuts down the backend (close pool, flush buffers, etc.).
     * Called on plugin disable — may block briefly.
     */
    void close();
}