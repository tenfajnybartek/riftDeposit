package pl.tenfajnybartek.riftdeposit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import pl.tenfajnybartek.riftdeposit.config.ConfigManager;
import pl.tenfajnybartek.riftdeposit.config.MessagesManager;
import pl.tenfajnybartek.riftdeposit.deposit.DepositData;
import pl.tenfajnybartek.riftdeposit.deposit.DepositManager;
import pl.tenfajnybartek.riftdeposit.deposit.ItemLimit;
import pl.tenfajnybartek.riftdeposit.deposit.ItemMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and refreshes the deposit GUI inventory.
 *
 * <p>Acts as the {@link InventoryHolder}, so we can identify it
 * in {@link GuiListener} via {@code inventory.getHolder() instanceof DepositGui}.</p>
 */
public final class DepositGui implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Inventory inventory;
    private final Player player;
    private final DepositManager depositManager;
    private final ConfigManager config;
    private final MessagesManager messages;

    public DepositGui(Player player, DepositManager depositManager, ConfigManager config, MessagesManager messages) {
        this.player = player;
        this.depositManager = depositManager;
        this.config = config;
        this.messages = messages;

        Component title = MM.deserialize(config.getGuiTitle());
        this.inventory = Bukkit.createInventory(this, config.getGuiRows() * 9, title);

        build();
    }

    // ── Build / Refresh ────────────────────────────────────────────────────

    /**
     * Fills (or refreshes) all slots. Safe to call repeatedly.
     */
    public void build() {
        inventory.clear();

        // 1. Background fill
        if (config.isGuiFillEmpty()) {
            ItemStack filler = makeItem(config.getGuiFillMaterial(), config.getGuiFillName(), List.of());
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        // 2. Item limits
        DepositData data = depositManager.getData(player.getUniqueId());

        for (ItemLimit limit : config.getItems()) {
            int slot = limit.getSlot();
            if (slot < 0 || slot >= inventory.getSize()) continue;

            inventory.setItem(slot, buildLimitIcon(limit, data));
        }

        // 3. Take-All button
        inventory.setItem(config.getTakeAllSlot(), buildTakeAllButton());

        // 4. Close button
        inventory.setItem(config.getCloseSlot(), buildCloseButton());
    }

    // ── Icon builders ──────────────────────────────────────────────────────

    private ItemStack buildLimitIcon(ItemLimit limit, DepositData data) {
        int stored       = data != null ? data.getStored(limit.getKey()) : 0;
        int maxForPlayer = limit.getMaxForPlayer(player, config.getGroups());
        int inInventory  = ItemMatcher.countInInventory(player.getInventory(), limit);
        int canTake      = Math.min(stored, Math.max(0, maxForPlayer - inInventory));

        // Display quantity in item count (clamp to valid range)
        int displayAmount = Math.max(1, Math.min(stored, limit.getMaterial().getMaxStackSize()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(messages.format("gui.lore-separator"));
        lore.add(messages.format("gui.lore-stored",      MessagesManager.stored(stored)));
        lore.add(messages.format("gui.lore-max",         MessagesManager.max(maxForPlayer)));
        lore.add(messages.format("gui.lore-in-inventory",MessagesManager.inInventory(inInventory)));
        lore.add(Component.empty());

        if (canTake > 0) {
            lore.add(messages.format("gui.lore-can-take-positive", MessagesManager.canTake(canTake)));
        } else {
            lore.add(messages.format("gui.lore-can-take-zero"));
        }

        lore.add(Component.empty());

        if (stored > 0) {
            lore.add(messages.format("gui.lore-hint-lmb"));
            lore.add(messages.format("gui.lore-hint-rmb"));
        } else {
            lore.add(messages.format("gui.lore-empty"));
        }

        ItemStack icon = ItemMatcher.buildItemStack(limit, displayAmount);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(MM.deserialize(limit.getDisplayName()));
        meta.lore(lore);
        // Prevent the item glow from base potion effects looking odd
        meta.setEnchantmentGlintOverride(false);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack buildTakeAllButton() {
        List<Component> lore = new ArrayList<>();
        String rawLore = config.getTakeAllLore();
        if (!rawLore.isBlank()) {
            for (String line : rawLore.split("\n")) {
                lore.add(MM.deserialize(line));
            }
        }
        return makeItem(config.getTakeAllMaterial(), config.getTakeAllName(), lore);
    }

    private ItemStack buildCloseButton() {
        return makeItem(config.getCloseMaterial(), config.getCloseName(), List.of());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ItemStack makeItem(Material material, String mmName, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MM.deserialize(mmName));
        if (!lore.isEmpty()) meta.lore(lore);
        meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── InventoryHolder ────────────────────────────────────────────────────

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}