package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.util.StringUtils;
import java.util.Locale;

/**
 * Renders a {@link Violation} as the block that shows up in logs.
 *
 * <p>The transaction line is deliberately first. A lone "you made an HTTP call" is easy to shrug
 * off; "you made an HTTP call 1.2 seconds into a transaction that is holding a connection"
 * is the sentence that makes someone fix it.
 */
public final class ViolationFormatter {

    private static final String HEADER = "[puretx] IMPURE TRANSACTION detected";
    private static final String INDENT = "             ";

    private ViolationFormatter() {
    }

    /** The multi-line report, without the call path. */
    public static String format(final Violation v) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(HEADER);
        sb.append("\n  tx       : ").append(transactionLine(v));
        sb.append("\n  violation: ").append(violationLine(v));
        if (v.origin() != null) {
            sb.append("\n  at       : ").append(v.origin());
        }
        sb.append("\n  hint     : ").append(v.type().hint().replace("\n", "\n" + INDENT));
        return sb.toString();
    }

    /**
     * The multi-line report followed by the chain of application frames that led to the violation.
     *
     * <p>Only the application's own frames, deliberately. The useful sentence is
     * "OrderService called PaymentGateway which called out", not forty frames of client internals.
     */
    public static String formatWithCallPath(final Violation v) {
        String body = format(v);
        if (v.callPath().isEmpty()) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body);
        sb.append("\n  path     :");
        for (StackTraceElement element : v.callPath()) {
            sb.append("\n").append(INDENT).append("at ").append(element);
        }
        return sb.toString();
    }

    /** A single line, for exception messages and one-line log formats. */
    public static String oneLine(final Violation v) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("[puretx] ").append(violationLine(v)).append(" inside transaction ")
                .append(v.transaction().displayName());
        if (v.origin() != null) {
            sb.append(" at ").append(v.origin());
        }
        return sb.toString();
    }

    private static String transactionLine(final Violation v) {
        TransactionInfo tx = v.transaction();
        StringBuilder sb = new StringBuilder(96);
        sb.append(tx.displayName());
        StringBuilder notes = new StringBuilder();
        if (tx.hasElapsed()) {
            if (v.type().isTransactionItself()) {
                notes.append("open for ").append(millis(tx.elapsedMillis()));
            } else {
                notes.append("started ").append(millis(tx.elapsedMillis())).append(" ago");
            }
        }
        if (tx.readOnly()) {
            append(notes, "read-only");
        }
        if (tx.testManaged()) {
            append(notes, "spring test-managed");
        }
        if (StringUtils.isNotBlank(tx.managerType())) {
            append(notes, tx.managerType());
        }
        if (notes.length() > 0) {
            sb.append(" (").append(notes).append(')');
        }
        return sb.toString();
    }

    private static String violationLine(final Violation v) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(StringUtils.isEmpty(v.summary()) ? v.type().displayName() : v.summary());
        if (v.hasDuration() && !v.type().isTransactionItself()) {
            sb.append("  (took ").append(millis(v.durationMillis())).append(')');
        }
        return sb.toString();
    }

    private static void append(final StringBuilder notes, final String text) {
        if (notes.length() > 0) {
            notes.append(", ");
        }
        notes.append(text);
    }

    private static String millis(final long value) {
        return String.format(Locale.ROOT, "%,dms", value);
    }
}
