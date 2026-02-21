package pl.tenfajnybartek.riftdeposit.deposit;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * Describes exactly which potion effect(s) an item must carry to match a limit.
 *
 * <p>Fields marked {@code @Nullable} act as wildcards — a {@code null} value
 * means "don't care about this attribute".</p>
 *
 * <h3>Matching rules</h3>
 * <ul>
 *   <li>{@link #effectType} — required; the potion must have an effect of this type.</li>
 *   <li>{@link #amplifier} — optional; {@code null} = match any level.
 *       0 = level I, 1 = level II, etc.</li>
 *   <li>{@link #minDurationTicks} / {@link #maxDurationTicks} — optional range filter.
 *       A {@code null} bound means "unbounded".</li>
 * </ul>
 *
 * <h3>Examples (config → spec)</h3>
 * <pre>
 * effect-type: STRENGTH            →  effectType=STRENGTH, amp=null, dur=null..null
 * effect-type: STRENGTH            →  only type checked, any level, any duration
 * amplifier: 1                     →  Strength II only
 * amplifier: 0
 * min-duration-ticks: 3600         →  >= 3 min (extended variants)
 * max-duration-ticks: 1800         →  <= 1:30 (standard variants)
 * </pre>
 */
public final class PotionSpec {

    private final PotionEffectType effectType;
    @Nullable private final Integer amplifier;
    @Nullable private final Integer minDurationTicks;
    @Nullable private final Integer maxDurationTicks;

    public PotionSpec(
            PotionEffectType effectType,
            @Nullable Integer amplifier,
            @Nullable Integer minDurationTicks,
            @Nullable Integer maxDurationTicks
    ) {
        this.effectType        = effectType;
        this.amplifier         = amplifier;
        this.minDurationTicks  = minDurationTicks;
        this.maxDurationTicks  = maxDurationTicks;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public PotionEffectType getEffectType()        { return effectType; }
    @Nullable public Integer getAmplifier()        { return amplifier; }
    @Nullable public Integer getMinDurationTicks() { return minDurationTicks; }
    @Nullable public Integer getMaxDurationTicks() { return maxDurationTicks; }

    /** Returns {@code true} if the given amplifier satisfies this spec. */
    public boolean matchesAmplifier(int amp) {
        return amplifier == null || amplifier == amp;
    }

    /** Returns {@code true} if the given duration (in ticks) satisfies this spec. */
    public boolean matchesDuration(int durationTicks) {
        if (minDurationTicks != null && durationTicks < minDurationTicks) return false;
        if (maxDurationTicks != null && durationTicks > maxDurationTicks) return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PotionSpec{effect=").append(effectType.key());
        if (amplifier != null)        sb.append(", amp=").append(amplifier);
        if (minDurationTicks != null) sb.append(", minDur=").append(minDurationTicks);
        if (maxDurationTicks != null) sb.append(", maxDur=").append(maxDurationTicks);
        return sb.append('}').toString();
    }
}