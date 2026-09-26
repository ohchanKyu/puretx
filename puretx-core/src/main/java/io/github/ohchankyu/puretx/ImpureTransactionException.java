package io.github.ohchankyu.puretx;

/**
 * Thrown at the offending call site when puretx runs in {@link PuretxMode#FAIL}.
 *
 * <p>This exists so that a test — and therefore a build — fails on a new violation.
 * Do not run {@code FAIL} in production: the exception aborts the very call it is complaining about.
 *
 * <p>The message is the full report including the call path: a CI failure is read from the
 * console, and the path is what says which line to open.
 *
 * <p>Carries the violation without its {@link TransactionInfo#source()}. An exception tends to be
 * kept — by a test report, an error tracker, a log appender — and the source is the live
 * transaction object, which through the framework reaches the connection and the persistence
 * context. The listeners already had the whole thing; nothing that outlives the transaction
 * should.
 */
public final class ImpureTransactionException extends RuntimeException {

    private final transient Violation violation;

    public ImpureTransactionException(final Violation violation) {
        super(ViolationFormatter.formatWithCallPath(violation));
        this.violation = violation.withoutSource();
    }

    public Violation violation() {
        return violation;
    }
}
