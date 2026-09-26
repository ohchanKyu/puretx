package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.Suppressions;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

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

    private static volatile Supplier<@Nullable PuretxEngine> scopedEngine = () -> null;

    private Puretx() {}

    /**
     * The engine for the current thread: the one that opened the transaction this thread is in,
     * if a framework has said so, otherwise the last one installed.
     *
     * <p>The distinction matters wherever more than one engine is alive at once, which a Spring
     * test suite with cached contexts is. The last context to start owns the static engine, but
     * a transaction opened by an earlier context's transaction manager belongs to that context,
     * and a {@link #watch} inside it must judge by that context's mode and record into that
     * context's store — not throw because some other context happens to run in {@code FAIL}.
     */
    public static PuretxEngine engine() {
        final PuretxEngine scoped = scopedEngine.get();
        return scoped != null ? scoped : engine;
    }

    /** Installed by the Spring Boot starter at startup. */
    public static void setEngine(final @Nullable PuretxEngine engine) {
        Puretx.engine = engine == null ? PuretxEngine.disabled() : engine;
    }

    /**
     * Installed by a framework integration that knows which engine opened the current
     * transaction. Returns {@code null} when the thread is in no transaction it knows about.
     */
    public static void setScopedEngine(final @Nullable Supplier<@Nullable PuretxEngine> resolver) {
        Puretx.scopedEngine = resolver == null ? () -> null : resolver;
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
    public static <T extends @Nullable Object> T suppress(final Supplier<T> action) {
        Suppressions.enter();
        try {
            return action.get();
        } finally {
            Suppressions.exit();
        }
    }

    /**
     * Reports {@code call} as an outbound HTTP call if a transaction is open, and times it.
     *
     * <p>The counterpart to {@link #suppress}: that one says "I know, and it is fine", this one
     * says "watch this, you cannot see it yourself". puretx instruments the Spring HTTP clients,
     * but a vendor SDK — Slack, AWS, a payment provider's own library — goes out over its own
     * stack and is invisible. Wrapping the call is what makes it visible:
     *
     * <pre>{@code
     * return Puretx.watch("Slack chat.postMessage",
     *         () -> slack.methods().chatPostMessage(request));
     * }</pre>
     *
     * <p>Costs nothing when there is no transaction open, and behaves like every other detector:
     * it logs in {@code WARN} and throws before the call in {@code FAIL}.
     */
    public static <T extends @Nullable Object> T watch(final String description, final Supplier<T> call) {
        return watch(ViolationType.HTTP_CALL, description, call);
    }

    /** {@link #watch(String, Supplier)} for a call that returns nothing. */
    public static void watch(final String description, final Runnable call) {
        watch(ViolationType.HTTP_CALL, description, () -> {
            call.run();
            return null;
        });
    }

    /** {@link #watch(String, Supplier)} for something other than an HTTP call — a publish, say. */
    public static <T extends @Nullable Object> T watch(final ViolationType type, final String description, final Supplier<T> call) {
        final PuretxEngine current = engine();
        final Detection detection = current.start(type, () -> description);
        if (detection == null) {
            return call.get();
        }
        try {
            return call.get();
        } finally {
            current.finish(detection);
        }
    }

    /** {@link #watch(ViolationType, String, Supplier)} for a call that returns nothing. */
    public static void watch(final ViolationType type, final String description, final Runnable call) {
        watch(type, description, () -> {
            call.run();
            return null;
        });
    }

    /** True while the current thread is inside {@link #suppress}. */
    public static boolean isSuppressed() {
        return Suppressions.active();
    }

    /**
     * The most recent violations, oldest first. Bounded by {@code puretx.record-limit}.
     *
     * <p><strong>One static engine.</strong> Each Spring context that starts installs its own,
     * and the last one to start wins. Inside a transaction this reads the engine that opened it,
     * but a test asserts after the transaction, where only the static one is left, and with
     * several contexts alive at once — a suite with cached contexts, most commonly — a violation
     * can be recorded in one store while this method reads another, and the assertion quietly
     * sees nothing.
     *
     * <p>In a test, inject the {@code PuretxEngine} bean and read {@code engine.store()} instead.
     * That is always the engine belonging to the context under test.
     */
    public static List<Violation> violations() {
        return engine().store().all();
    }

    /** Total violations since the last {@link #clearViolations()}, including ones dropped by the limit. */
    public static long violationCount() {
        return engine().store().total();
    }

    public static void clearViolations() {
        engine().store().clear();
    }
}
