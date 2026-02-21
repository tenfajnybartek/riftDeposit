package pl.tenfajnybartek.riftdeposit.deposit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Immutable descriptor of a single tracked item type.
 *
 * <p>For potions, {@link #potionSpec} provides precise matching by effect type,
 * amplifier (level) and duration range — replacing the old simple {@code PotionType}
 * match that couldn't distinguish Strength I from Strength II or extended variants.</p>
 */
public final class ItemLimit {

    private final String key;
    private final Material material;

    /**
     * Non-null when this item is a potion (POTION / SPLASH_POTION / LINGERING_POTION).
     * Null for non-potion materials.
     */
    @Nullable private final PotionSpec potionSpec;

    /** Raw MiniMessage string shown in the GUI. */
    private final String displayName;

    /** GUI slot index (0-based). */
    private final int slot;

    /** group-key → max amount. Should always include a "default" key. */
    private final Map<String, Integer> groupLimits;

    // ── Construction ───────────────────────────────────────────────────────

    public ItemLimit(
            String key,
            Material material,
            @Nullable PotionSpec potionSpec,
            String displayName,
            int slot,
            Map<String, Integer> groupLimits
    ) {
        this.key          = key;
        this.material     = material;
        this.potionSpec   = potionSpec;
        this.displayName  = displayName;
        this.slot         = slot;
        this.groupLimits  = Map.copyOf(groupLimits);
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getKey()                        { return key; }
    public Material getMaterial()                 { return material; }
    @Nullable public PotionSpec getPotionSpec()   { return potionSpec; }
    public String getDisplayName()                { return displayName; }
    public int getSlot()                          { return slot; }
    public Map<String, Integer> getGroupLimits()  { return groupLimits; }

    /** Returns {@code true} when this limit applies to potions (has a PotionSpec). */
    public boolean isPotion() {
        return potionSpec != null;
    }

    // ── Limit resolution ───────────────────────────────────────────────────

    /**
     * Returns the effective item max for {@code player} by walking
     * {@code groups} (sorted priority-DESC) and returning the first
     * limit entry that exists for a group the player qualifies for.
     */
    public int getMaxForPlayer(Player player, List<LimitGroup> groups) {
        for (LimitGroup group : groups) {
            if (group.getPermission() == null || player.hasPermission(group.getPermission())) {
                Integer limit = groupLimits.get(group.getKey());
                if (limit != null) return limit;
            }
        }
        return groupLimits.getOrDefault("default", 0);
    }

    /** Highest limit across all groups — used for GUI display hints. */
    public int getAbsoluteMax() {
        return groupLimits.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    @Override
    public String toString() {
        return "ItemLimit{key='" + key + "', material=" + material
                + (potionSpec != null ? ", potionSpec=" + potionSpec : "")
                + ", groupLimits=" + groupLimits + '}';
    }
}