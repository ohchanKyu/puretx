package io.github.ohchankyu.puretx;

/**
 * What puretx does when it finds work that should not be happening inside a transaction.
 *
 * <p>puretx never rolls back, never retries and never rewrites your call. The strongest
 * thing it will do is throw, and that exists so a build can fail — not so production traffic can.
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
     */
    FAIL;

    public boolean isActive() {
        return this != OFF;
    }
}
