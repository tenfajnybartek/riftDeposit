package pl.tenfajnybartek.riftdeposit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.gui.DepositGui;

/**
 * Handles player data lifecycle and reactive inventory checks.
 *
 * <ul>
 *   <li>Join  → async-load deposit data</li>
 *   <li>Quit  → save &amp; evict from cache</li>
 *   <li>Item pickup → 1-tick delayed {@code checkAndDeposit}</li>
 *   <li>Inventory close (non-GUI) → 1-tick delayed {@code checkAndDeposit}</li>
 * </ul>
 *
 * <p>Note: closing the {@link DepositGui} is intentionally ignored here
 * to avoid redundant checks after every GUI interaction.</p>
 */
public final class InventoryCheckListener implements Listener {

    private final JavaPlugin plugin;
    private final DepositManager depositManager;

    public InventoryCheckListener(JavaPlugin plugin, DepositManager depositManager) {
        this.plugin = plugin;
        this.depositManager = depositManager;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        depositManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        depositManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    // ── Inventory monitoring ───────────────────────────────────────────────

    /**
     * Called when a player picks up an item from the ground.
     * Delayed 1 tick so Bukkit finishes adding the item to the inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin,
                () -> depositManager.checkAndDeposit(player));
    }

    /**
     * Catches items moved from containers (chests, crafting tables, etc.).
     *
     * <p><b>Bug fix:</b> skips the check when the closed inventory is the
     * {@link DepositGui} itself — closing the GUI fires this event too,
     * causing a wasteful and confusing redundant check right after
     * the player has just taken items via the GUI.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Skip if the player just closed our own DepositGui
        if (event.getInventory().getHolder() instanceof DepositGui) return;

        plugin.getServer().getScheduler().runTask(plugin,
                () -> depositManager.checkAndDeposit(player));
    }
}