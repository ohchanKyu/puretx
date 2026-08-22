package io.github.ohchankyu.puretx.internal;

/**
 * A re-entrant, thread-local "don't look at this" flag.
 *
 * <p>Internal to puretx — use {@code Puretx.suppress(...)} instead.
 */
public final class Suppressions {

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<>();

    private Suppressions() {}

    public static boolean active() {
        int[] depth = DEPTH.get();
        return depth != null && depth[0] > 0;
    }

    public static void enter() {
        int[] depth = DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            DEPTH.set(depth);
        }
        depth[0]++;
    }

    public static void exit() {
        int[] depth = DEPTH.get();
        if (depth == null) {
            return;
        }
        if (--depth[0] <= 0) {
            DEPTH.remove();
        }
    }
}
