package io.github.ohchankyu.puretx;

/**
 * What puretx does when it finds work that should not be happening inside a transaction.
 *
 * <p>puretx never retries and never rewrites your call. The strongest thing it will do is throw,
 * and that exists so a build can fail — not so production traffic can. A thrown exception rolls
 * a Spring transaction back the way any exception does, which is the second reason {@link #FAIL}
 * belongs in tests.
 */
public enum PuretxMode {

    /** Detection is off. No stack walking, no bookkeeping, no logging. */
    OFF,

    /**
     * Log the violation and let the call proceed untouched.
     * The right choice for production and local development.
     */
    WARN,

    /**
     * Throw {@link ImpureTransactionException} at the offending call site.
     * Intended for tests and CI, where a new violation should break the build.
     *
     * <p>The exception aborts the unit of work: for a call, it is thrown before the call inside
     * the transactional method, and for a transaction held too long, just before the commit.
     * Either way the transaction rolls back and nothing it wrote survives.
     */
    FAIL;

    public boolean isActive() {
        return this != OFF;
    }
}
