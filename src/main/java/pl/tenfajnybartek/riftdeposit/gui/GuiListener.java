package pl.tenfajnybartek.riftdeposit.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.deposit.ItemLimit;
import pl.tenfajnybartek.riftdeposit.deposit.ItemMatcher;

import java.util.Optional;

/**
 * Handles player interactions inside the {@link DepositGui}.
 *
 * <p>Rules:
 * <ul>
 *   <li>All clicks inside the GUI are cancelled to prevent item theft.</li>
 *   <li>LPM on an item slot → take 1.</li>
 *   <li>PPM on an item slot → take all available (up to limit).</li>
 *   <li>Click on "Take-All" → {@link DepositManager#takeAll}</li>
 *   <li>Click on "Close" → close inventory.</li>
 *   <li>All actions refresh the GUI in place afterwards.</li>
 * </ul>
 * </p>
 */
public final class GuiListener implements Listener {

    private final DepositManager depositManager;
    private final ConfigManager config;

    public GuiListener(DepositManager depositManager, ConfigManager config) {
        this.depositManager = depositManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DepositGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Always cancel — no items should be moved out of the GUI directly
        event.setCancelled(true);

        // Only care about top inventory clicks
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(gui.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        ClickType click = event.getClick();

        // ── Close button ─────────────────────────────────────────────────
        if (slot == config.getCloseSlot()) {
            player.closeInventory();
            return;
        }

        // ── Take-All button ──────────────────────────────────────────────
        if (slot == config.getTakeAllSlot()) {
            depositManager.takeAll(player);
            gui.build();
            return;
        }

        // ── Item slot: find matching ItemLimit by slot index ─────────────
        Optional<ItemLimit> limitOpt = config.getItems().stream()
                .filter(l -> l.getSlot() == slot)
                .findFirst();

        if (limitOpt.isEmpty()) return; // clicked filler or unknown slot

        ItemLimit limit = limitOpt.get();

        if (click.isLeftClick()) {
            // LPM → take 1
            depositManager.takeItem(player, limit, 1);
        } else if (click.isRightClick()) {
            // PPM → take as many as possible up to limit
            int maxForPlayer = limit.getMaxForPlayer(player, config.getGroups());
            int inInventory  = ItemMatcher.countInInventory(player.getInventory(), limit);
            int canRequest   = Math.max(0, maxForPlayer - inInventory);
            if (canRequest > 0) {
                depositManager.takeItem(player, limit, canRequest);
            }
        }

        // Refresh GUI to reflect updated counts
        gui.build();
    }

    /**
     * Prevent drag-placing items into the GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DepositGui) {
            event.setCancelled(true);
        }
    }
}