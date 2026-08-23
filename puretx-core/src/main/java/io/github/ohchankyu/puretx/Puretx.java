package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.Suppressions;
import java.util.List;
import java.util.function.Supplier;

/**
 * Static entry point to puretx.
 *
 * <p>Most of the time you never touch this class — the Spring Boot starter wires everything up and
 * puretx just talks to you through the log. It is here for the two things that genuinely need a
 * static hook: silencing a call site you have already thought about, and asserting on violations
 * in a test.
 *
 * <pre>{@code
 * // "yes, I know, and it is fine": a fast, idempotent call we deliberately keep inside the transaction
 * Puretx.suppress(() -> auditClient.record(event));
 *
 * // in a test
 * assertThat(Puretx.violations()).isEmpty();
 * }</pre>
 */
public final class Puretx {

    /** The logger everything in puretx writes under, so one line of config turns it all down. */
    public static final String LOGGER_NAME = "io.github.ohchankyu.puretx";

    private static volatile PuretxEngine engine = PuretxEngine.disabled();

    private Puretx() {}

    public static PuretxEngine engine() {
        return engine;
    }

    /** Installed by the Spring Boot starter at startup. */
    public static void setEngine(final PuretxEngine engine) {
        Puretx.engine = engine == null ? PuretxEngine.disabled() : engine;
    }

    /**
     * Runs {@code action} with detection turned off on this thread.
     *
     * <p>Re-entrant, and restored even if the action throws. Use it to acknowledge a violation you
     * have decided to live with, so the log keeps reporting only the ones you have not.
     */
    public static void suppress(final Runnable action) {
        Suppressions.enter();
        try {
            action.run();
        } finally {
            Suppressions.exit();
        }
    }

    /** {@link #suppress(Runnable)} for calls that return something. */
    public static <T> T suppress(final Supplier<T> action) {
        Suppressions.enter();
        try {
            return action.get();
        } finally {
            Suppressions.exit();
        }
    }

    /** True while the current thread is inside {@link #suppress}. */
    public static boolean isSuppressed() {
        return Suppressions.active();
    }

    /**
     * The most recent violations, oldest first. Bounded by {@code puretx.record-limit}.
     *
     * <p><strong>One engine, globally.</strong> Each Spring context that starts installs its own,
     * and the last one to start wins. Interceptors keep hold of the engine from the context that
     * built them, so with several contexts alive at once — a test suite running classes in
     * parallel, most commonly — a violation can be recorded in one store while this method reads
     * another, and the assertion quietly sees nothing.
     *
     * <p>In a test, inject the {@code PuretxEngine} bean and read {@code engine.store()} instead.
     * That is always the engine belonging to the context under test.
     */
    public static List<Violation> violations() {
        return engine.store().all();
    }

    /** Total violations since the last {@link #clearViolations()}, including ones dropped by the limit. */
    public static long violationCount() {
        return engine.store().total();
    }

    public static void clearViolations() {
        engine.store().clear();
    }
}
