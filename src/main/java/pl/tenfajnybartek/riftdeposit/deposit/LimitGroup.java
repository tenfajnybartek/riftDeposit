package pl.tenfajnybartek.riftdeposit.deposit;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a named limit group (e.g. "vip", "svip", "default").
 * Groups are checked in descending priority order; the first group
 * whose permission the player holds is used to resolve item limits.
 */
public final class LimitGroup {

    private final String key;
    @Nullable private final String permission;
    private final int priority;

    public LimitGroup(String key, @Nullable String permission, int priority) {
        this.key = key;
        this.permission = permission;
        this.priority = priority;
    }

    /** Internal config key (e.g. "vip"). */
    public String getKey() {
        return key;
    }

    /**
     * Bukkit permission string that grants this group.
     * {@code null} means this group is always active (fallback / default).
     */
    @Nullable
    public String getPermission() {
        return permission;
    }

    /**
     * Higher value = checked first.
     * The first group the player qualifies for is used.
     */
    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "LimitGroup{key='" + key + "', permission='" + permission + "', priority=" + priority + '}';
    }
}