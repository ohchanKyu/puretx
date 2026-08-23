package io.github.ohchankyu.puretx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, in-memory ring of the most recent violations.
 *
 * <p>Its reason for existing is assertions: {@code assertThat(Puretx.violations()).isEmpty()}
 * reads better in a test than scraping a log. Oldest entries are dropped once the limit is hit.
 */
public final class ViolationStore {

    private final int limit;

    private final Deque<Violation> recent = new ArrayDeque<>();

    private long total;

    public ViolationStore(final int limit) {
        this.limit = limit;
    }

    public synchronized void add(final Violation violation) {
        total++;
        if (limit <= 0) {
            return;
        }
        if (recent.size() >= limit) {
            recent.removeFirst();
        }
        recent.addLast(violation);
    }

    /** Most recent last. */
    public synchronized List<Violation> all() {
        return List.copyOf(recent);
    }

    public synchronized void clear() {
        recent.clear();
        total = 0;
    }

    /** Number of violations recorded since the last {@link #clear()}, including dropped ones. */
    public synchronized long total() {
        return total;
    }
}
