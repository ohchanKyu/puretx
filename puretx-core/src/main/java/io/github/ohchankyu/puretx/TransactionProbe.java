package io.github.ohchankyu.puretx;

/**
 * Tells the engine whether the calling thread is inside a transaction that puretx should police.
 *
 * <p>This is the one piece that knows about a specific framework. The Spring implementation
 * lives in {@code puretx-spring-boot-starter}; anything else can plug in its own.
 *
 * <p>Implementations must return {@code null} — not a transaction — while post-commit callbacks
 * are running. Work deferred to {@code afterCommit} is exactly what puretx tells people to do,
 * so flagging it would be self-defeating.
 */
@FunctionalInterface
public interface TransactionProbe {

    /** A probe that never sees a transaction. */
    TransactionProbe NONE = () -> null;

    /**
     * @return a snapshot of the transaction currently open on this thread, or {@code null}
     *         if there is none, or if the thread is somewhere puretx should stay quiet.
     */
    TransactionInfo currentTransaction();
}
