package io.github.ohchankyu.puretx;

import java.time.Instant;
import java.util.List;

/**
 * A violation that has been identified but not yet timed.
 *
 * <p>Detectors get one of these from {@link PuretxEngine#start}, run the operation, and hand it
 * back to {@link PuretxEngine#finish}, which reads the elapsed time off the token rather than
 * making every detector re-derive it. A {@code null} detection means "nothing to see here" and
 * every engine method accepts it, so detectors need no null checks.
 */
public final class Detection {

    private final ViolationType type;

    private final String summary;

    private final TransactionInfo transaction;

    private final StackTraceElement origin;

    private final List<StackTraceElement> callPath;

    private final long startNanos;

    Detection(
        final ViolationType type,
        final String summary,
        final TransactionInfo transaction,
        final StackTraceElement origin,
        final List<StackTraceElement> callPath
    ) {
        this.type = type;
        this.summary = summary;
        this.transaction = transaction;
        this.origin = origin;
        this.callPath = callPath;
        this.startNanos = System.nanoTime();
    }

    public ViolationType type() {
        return type;
    }

    public String summary() {
        return summary;
    }

    public TransactionInfo transaction() {
        return transaction;
    }

    /** How long the operation has been running, in milliseconds. */
    long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    Violation toViolation(final long durationMillis, final Instant occurredAt) {
        return new Violation(type, summary, durationMillis, transaction, origin, callPath, occurredAt);
    }
}
