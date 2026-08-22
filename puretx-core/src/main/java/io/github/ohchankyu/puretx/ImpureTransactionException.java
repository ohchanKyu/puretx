package io.github.ohchankyu.puretx;

/**
 * Thrown at the offending call site when puretx runs in {@link PuretxMode#FAIL}.
 *
 * <p>This exists so that a test — and therefore a build — fails on a new violation.
 * Do not run {@code FAIL} in production: the exception aborts the very call it is complaining about.
 */
public final class ImpureTransactionException extends RuntimeException {

    private final transient Violation violation;

    public ImpureTransactionException(final Violation violation) {
        super(ViolationFormatter.format(violation));
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}
