package pl.tenfajnybartek.riftdeposit.deposit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

/**
 * Utility for matching, counting, removing and adding items based on {@link ItemLimit}.
 *
 * <h3>Potion matching logic (Paper 1.21)</h3>
 * <p>In 1.20.5+ Minecraft moved potions to data-components. Paper's PotionMeta still works,
 * but effects are sourced from two places:</p>
 * <ol>
 *   <li>{@code PotionMeta.getBasePotionType().getPotionEffects()} — effects from the
 *       "base" potion type (vanilla recipes).  These carry the canonical level and duration
 *       for that recipe (e.g. Strength: amp=0 dur=3600; Strong Strength: amp=1 dur=1800).</li>
 *   <li>{@code PotionMeta.getCustomEffects()} — explicitly added effects (e.g. via
 *       {@code /give} with custom NBT, or plugin-created potions).</li>
 * </ol>
 * <p>We check <em>all</em> effects (custom first, then base) and test each one against
 * the {@link PotionSpec}.  A match requires the effect type to match, and optionally
 * the amplifier and duration range to satisfy the spec.</p>
 *
 * <h3>Important: why we don't use PotionType alone</h3>
 * <p>{@code PotionType.STRENGTH} and a "Strength II" potion both have the same
 * {@code PotionType}, but differ in amplifier. Without checking the actual
 * {@code PotionEffect} amplifier you'd count Strength I and II together — wrong.</p>
 */
public final class ItemMatcher {

    private ItemMatcher() {}

    // ── Matching ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code item} matches the limit descriptor.
     *
     * <ul>
     *   <li>Material must match exactly.</li>
     *   <li>If the limit has a {@link PotionSpec}, the item's actual effects
     *       (base + custom) are checked against it.</li>
     * </ul>
     */
    public static boolean matches(ItemStack item, ItemLimit limit) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() != limit.getMaterial()) return false;

        PotionSpec spec = limit.getPotionSpec();
        if (spec == null) return true; // non-potion item — material match is enough

        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;

        return findMatchingEffect(meta, spec);
    }

    /**
     * Searches all effects on the potion (custom first, then base) for one
     * that satisfies the spec. Returns {@code true} on first match.
     */
    private static boolean findMatchingEffect(PotionMeta meta, PotionSpec spec) {
        // 1. Custom effects (added via /give, plugins, etc.)
        Collection<PotionEffect> custom = meta.getCustomEffects();
        for (PotionEffect effect : custom) {
            if (effectMatches(effect, spec)) return true;
        }

        // 2. Base potion type effects (vanilla recipes)
        //    getBasePotionType() may be null for potions with only custom effects
        var baseType = meta.getBasePotionType();
        if (baseType != null) {
            for (PotionEffect effect : baseType.getPotionEffects()) {
                if (effectMatches(effect, spec)) return true;
            }
        }

        return false;
    }

    /**
     * Tests a single {@link PotionEffect} against a {@link PotionSpec}.
     */
    private static boolean effectMatches(PotionEffect effect, PotionSpec spec) {
        // Effect type must match
        if (!spec.getEffectType().equals(effect.getType())) return false;

        // Amplifier check (0 = level I, 1 = level II, ...)
        if (!spec.matchesAmplifier(effect.getAmplifier())) return false;

        // Duration check
        if (!spec.matchesDuration(effect.getDuration())) return false;

        return true;
    }

    // ── Counting ───────────────────────────────────────────────────────────

    /** Counts matching items in the player's 36-slot storage (no armour/offhand). */
    public static int countInInventory(PlayerInventory inventory, ItemLimit limit) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (matches(item, limit)) total += item.getAmount();
        }
        return total;
    }

    // ── Removing ───────────────────────────────────────────────────────────

    /**
     * Removes up to {@code amount} matching items from the player's storage.
     * Returns the number actually removed.
     */
    public static int removeFromInventory(Player player, ItemLimit limit, int amount) {
        if (amount <= 0) return 0;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!matches(item, limit)) continue;

            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
        }

        player.getInventory().setStorageContents(contents);
        return amount - remaining;
    }

    // ── Adding ─────────────────────────────────────────────────────────────

    /**
     * Adds up to {@code amount} items to the player's inventory.
     * Returns the number actually added (may be less if inventory is full).
     */
    public static int addToInventory(Player player, ItemLimit limit, int amount) {
        if (amount <= 0) return 0;

        int remaining = amount;
        int maxStack  = limit.getMaterial().getMaxStackSize();

        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack stack = buildItemStack(limit, stackSize);

            var overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                int notFit = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                remaining -= (stackSize - notFit);
                break;
            }
            remaining -= stackSize;
        }

        return amount - remaining;
    }

    /** {@code true} if the player has room for at least {@code amount} of this item. */
    public static boolean hasSpaceFor(Player player, ItemLimit limit, int amount) {
        int freeSpace = 0;
        int maxStack  = limit.getMaterial().getMaxStackSize();

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                freeSpace += maxStack;
            } else if (matches(item, limit) && item.getAmount() < maxStack) {
                freeSpace += maxStack - item.getAmount();
            }
            if (freeSpace >= amount) return true;
        }
        return freeSpace >= amount;
    }

    // ── ItemStack builder ──────────────────────────────────────────────────

    /**
     * Builds a clean {@link ItemStack} for a limit.
     * For potions, the spec's effect type, amplifier and duration are applied as
     * a custom effect so the item is an exact copy of what the server tracks.
     */
    public static ItemStack buildItemStack(ItemLimit limit, int amount) {
        ItemStack stack = new ItemStack(limit.getMaterial(), amount);

        PotionSpec spec = limit.getPotionSpec();
        if (spec != null && stack.getItemMeta() instanceof PotionMeta meta) {
            // Duration: use midpoint of range, or 3600 (3 min) as default
            int duration = resolveBuildDuration(spec);
            int amplifier = spec.getAmplifier() != null ? spec.getAmplifier() : 0;

            PotionEffect effect = new PotionEffect(
                    spec.getEffectType(),
                    duration,
                    amplifier,
                    false,  // ambient (beacon-style particles)
                    true,   // particles visible
                    true    // show icon
            );
            meta.addCustomEffect(effect, true);
            stack.setItemMeta(meta);
        }

        return stack;
    }

    /**
     * Determines the duration to use when synthesising a potion ItemStack.
     * <ul>
     *   <li>If an exact duration is specified (min == max), use that.</li>
     *   <li>If only a range is given, use the minimum bound.</li>
     *   <li>Otherwise fall back to 3600 ticks (3 minutes).</li>
     * </ul>
     */
    private static int resolveBuildDuration(PotionSpec spec) {
        Integer min = spec.getMinDurationTicks();
        Integer max = spec.getMaxDurationTicks();

        if (min != null && max != null && min.equals(max)) return min; // exact
        if (min != null) return min;
        if (max != null) return max;
        return 3600; // default 3 min
    }
}