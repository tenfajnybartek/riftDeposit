package pl.tenfajnybartek.riftdeposit.deposit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the deposit vault contents for a single player.
 * Kept in-memory while online, persisted on quit / periodically.
 *
 * <p>Thread-safety: all mutating methods are {@code synchronized}.
 * Storage providers must call {@link #snapshot()} to obtain a safe,
 * immutable copy of the data before processing it on an async thread.</p>
 */
public final class DepositData {

    private final UUID playerUuid;

    /**
     * The live map.  All access (read AND write) must be synchronized on {@code this}.
     */
    private final Map<String, Integer> stored;

    public DepositData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.stored = new HashMap<>();
    }

    public DepositData(UUID playerUuid, Map<String, Integer> stored) {
        this.playerUuid = playerUuid;
        this.stored = new HashMap<>(stored);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    // ── Accessors — all synchronized ──────────────────────────────────────

    public synchronized int getStored(String itemKey) {
        return stored.getOrDefault(itemKey, 0);
    }

    public synchronized void setStored(String itemKey, int amount) {
        if (amount <= 0) stored.remove(itemKey);
        else             stored.put(itemKey, amount);
    }

    public synchronized void addStored(String itemKey, int amount) {
        if (amount <= 0) return;
        stored.merge(itemKey, amount, Integer::sum);
    }

    public synchronized void subtractStored(String itemKey, int amount) {
        if (amount <= 0) return;
        int current = stored.getOrDefault(itemKey, 0);
        int newVal  = current - amount;
        if (newVal <= 0) stored.remove(itemKey);
        else             stored.put(itemKey, newVal);
    }

    public synchronized boolean isEmpty() {
        return stored.isEmpty();
    }

    /** Unmodifiable live-view for same-thread iteration (GUI, admin inspect). */
    public synchronized Map<String, Integer> getAll() {
        return Collections.unmodifiableMap(stored);
    }

    /**
     * Returns a <b>safe, detached copy</b> of the current state.
     * Call this on the main thread and pass the result to async I/O —
     * never pass the {@code DepositData} object itself to a background thread.
     */
    public synchronized Map<String, Integer> snapshot() {
        return new HashMap<>(stored);
    }

    public synchronized void clear() {
        stored.clear();
    }

    @Override
    public String toString() {
        return "DepositData{uuid=" + playerUuid + ", stored=" + stored + '}';
    }
}