package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.util.CollectionUtils;
import java.time.Instant;
import java.util.List;

/**
 * One detected piece of impure work.
 *
 * @param type           what kind of impurity this is
 * @param summary        the concrete operation, e.g. {@code HTTP POST https://pay.example.com/charge}
 * @param durationMillis how long the operation itself took, or {@code -1} if not measured
 * @param transaction    the transaction that was open at the time
 * @param origin         the first application frame on the stack, or {@code null} if none was found
 * @param callPath       the chain of application frames that led here, innermost first;
 *                       empty when call-path capture is disabled
 * @param occurredAt     wall-clock time of detection
 */
public record Violation(
        ViolationType type,
        String summary,
        long durationMillis,
        TransactionInfo transaction,
        StackTraceElement origin,
        List<StackTraceElement> callPath,
        Instant occurredAt) {

    /** {@code durationMillis} when the operation was not, or could not be, timed. */
    public static final long UNKNOWN_DURATION = -1;

    public Violation {
        callPath = CollectionUtils.isEmpty(callPath) ? List.of() : List.copyOf(callPath);
    }

    public boolean hasDuration() {
        return durationMillis > UNKNOWN_DURATION;
    }

    @Override
    public String toString() {
        return ViolationFormatter.oneLine(this);
    }
}
