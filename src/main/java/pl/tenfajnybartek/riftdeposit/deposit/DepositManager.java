package pl.tenfajnybartek.riftdeposit.deposit;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.config.MessagesManager;
import pl.tenfajnybartek.riftdeposit.data.StorageProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for all deposit operations.
 *
 * <p>Data flow:
 * <ol>
 *   <li>Player joins → async load → store in {@link #cache}</li>
 *   <li>Periodic task + events → {@link #checkAndDeposit(Player)}</li>
 *   <li>Player interacts with GUI → {@link #takeItem} / {@link #takeAll}</li>
 *   <li>Player quits → async save → evict from cache</li>
 * </ol>
 * </p>
 *
 * <p>All inventory manipulation runs on the <b>main thread</b>.
 * All I/O runs on <b>async threads</b> via {@link StorageProvider}.</p>
 */
public final class DepositManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MessagesManager messages;
    private final StorageProvider storage;
    private final Logger log;

    /**
     * In-memory cache: UUID → DepositData.
     * Only populated for currently online players.
     */
    private final Map<UUID, DepositData> cache = new ConcurrentHashMap<>();

    /**
     * UUIDs whose data is currently being loaded (to avoid double-loads).
     */
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public DepositManager(
            JavaPlugin plugin,
            ConfigManager config,
            MessagesManager messages,
            StorageProvider storage
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.storage = storage;
        this.log = plugin.getLogger();
    }

    // ── Player lifecycle ───────────────────────────────────────────────────

    /**
     * Loads player data asynchronously and caches it.
     * Safe to call from any thread.
     */
    public void loadPlayer(UUID uuid) {
        if (cache.containsKey(uuid) || !loading.add(uuid)) return;

        storage.load(uuid).thenAcceptAsync(data -> {
            cache.put(uuid, data);
            loading.remove(uuid);
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * Saves player data asynchronously and removes from cache.
     * Safe to call from any thread.
     */
    public void unloadPlayer(UUID uuid) {
        DepositData data = cache.remove(uuid);
        loading.remove(uuid);
        if (data != null) {
            storage.save(data);
        }
    }

    /** Returns {@code true} if the player's data is loaded and ready. */
    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** Gets cached data, or {@code null} if not loaded. */
    public DepositData getData(UUID uuid) {
        return cache.get(uuid);
    }

    // ── Core Logic ─────────────────────────────────────────────────────────

    /**
     * Checks the player's inventory for excess items and moves them to the deposit.
     * Must be called on the <b>main thread</b>.
     *
     * @return total number of item stacks moved (for logging)
     */
    public int checkAndDeposit(Player player) {
        if (player.hasPermission("riftdeposit.bypass")) return 0;

        DepositData data = cache.get(player.getUniqueId());
        if (data == null) return 0; // data not loaded yet

        List<LimitGroup> groups = config.getGroups();
        int totalMoved = 0;

        for (ItemLimit limit : config.getItems()) {
            int inInventory = ItemMatcher.countInInventory(player.getInventory(), limit);
            int max         = limit.getMaxForPlayer(player, groups);

            if (inInventory > max) {
                int excess = inInventory - max;
                int removed = ItemMatcher.removeFromInventory(player, limit, excess);

                if (removed > 0) {
                    data.addStored(limit.getKey(), removed);
                    totalMoved++;

                    messages.send(player, "deposit.excess-moved",
                            MessagesManager.amount(removed),
                            MessagesManager.item(limit.getDisplayName())
                    );
                }
            }
        }

        if (totalMoved > 0) {
            scheduleSave(player.getUniqueId());
        }
        return totalMoved;
    }

    /**
     * Takes {@code requestedAmount} of the given item from the deposit to the
     * player's inventory. Sends appropriate feedback messages.
     * Must be called on the <b>main thread</b>.
     *
     * @return number of items actually taken
     */
    public int takeItem(Player player, ItemLimit limit, int requestedAmount) {
        DepositData data = cache.get(player.getUniqueId());
        if (data == null) {
            messages.send(player, "errors.data-not-loaded");
            return 0;
        }

        int stored = data.getStored(limit.getKey());
        if (stored <= 0) {
            messages.send(player, "deposit.deposit-empty");
            return 0;
        }

        int maxForPlayer = limit.getMaxForPlayer(player, config.getGroups());
        int inInventory  = ItemMatcher.countInInventory(player.getInventory(), limit);
        int canTake      = Math.min(stored, Math.max(0, maxForPlayer - inInventory));
        canTake          = Math.min(canTake, requestedAmount);

        if (canTake <= 0) {
            if (inInventory >= maxForPlayer) {
                messages.send(player, "deposit.at-limit",
                        MessagesManager.item(limit.getDisplayName()));
            } else {
                messages.send(player, "deposit.deposit-empty");
            }
            return 0;
        }

        if (!ItemMatcher.hasSpaceFor(player, limit, canTake)) {
            messages.send(player, "deposit.no-space");
            return 0;
        }

        int added = ItemMatcher.addToInventory(player, limit, canTake);
        if (added > 0) {
            data.subtractStored(limit.getKey(), added);
            messages.send(player, "deposit.item-taken",
                    MessagesManager.amount(added),
                    MessagesManager.item(limit.getDisplayName())
            );
            scheduleSave(player.getUniqueId());
        }
        return added;
    }

    /**
     * Takes all possible items from the deposit for this player.
     * Must be called on the <b>main thread</b>.
     *
     * @return total number of item-type stacks taken
     */
    public int takeAll(Player player) {
        DepositData data = cache.get(player.getUniqueId());
        if (data == null) {
            messages.send(player, "errors.data-not-loaded");
            return 0;
        }

        if (data.isEmpty()) {
            messages.send(player, "deposit.deposit-empty");
            return 0;
        }

        List<LimitGroup> groups = config.getGroups();
        int totalTaken = 0;

        for (ItemLimit limit : config.getItems()) {
            int stored = data.getStored(limit.getKey());
            if (stored <= 0) continue;

            int maxForPlayer = limit.getMaxForPlayer(player, groups);
            int inInventory  = ItemMatcher.countInInventory(player.getInventory(), limit);
            int canTake      = Math.min(stored, Math.max(0, maxForPlayer - inInventory));

            if (canTake <= 0) continue;
            if (!ItemMatcher.hasSpaceFor(player, limit, 1)) continue; // skip if no space at all

            int added = ItemMatcher.addToInventory(player, limit, canTake);
            if (added > 0) {
                data.subtractStored(limit.getKey(), added);
                totalTaken++;
            }
        }

        if (totalTaken > 0) {
            messages.send(player, "deposit.items-taken-all");
            scheduleSave(player.getUniqueId());
        } else {
            messages.send(player, "deposit.nothing-to-take");
        }
        return totalTaken;
    }

    // ── Admin helpers ──────────────────────────────────────────────────────

    /**
     * Clears the entire deposit for a player.
     * Works even if the player is offline (saves immediately to storage).
     */
    public void adminClear(UUID uuid) {
        DepositData data = cache.get(uuid);
        if (data != null) {
            data.clear();
            scheduleSave(uuid);
        } else {
            // Offline player — delete from storage directly
            storage.delete(uuid);
        }
    }

    /**
     * Clears a specific item from a player's deposit.
     */
    public void adminClearItem(UUID uuid, String itemKey) {
        DepositData data = cache.get(uuid);
        if (data != null) {
            data.setStored(itemKey, 0);
            scheduleSave(uuid);
        }
    }

    /**
     * Adds items to a player's deposit (bypasses limits — admin action).
     */
    public void adminGive(UUID uuid, String itemKey, int amount) {
        DepositData data = cache.get(uuid);
        if (data != null) {
            data.addStored(itemKey, amount);
            scheduleSave(uuid);
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Saves all online players' data — called on plugin disable.
     */
    public void saveAll() {
        int count = 0;
        for (Map.Entry<UUID, DepositData> entry : cache.entrySet()) {
            storage.save(entry.getValue());
            count++;
        }
        if (count > 0) log.info("Queued save for " + count + " online player(s).");
    }

    private void scheduleSave(UUID uuid) {
        DepositData data = cache.get(uuid);
        if (data != null) {
            storage.save(data); // runs async inside the provider
        }
    }
}