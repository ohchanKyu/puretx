package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Registered at the start of every transaction, at the highest precedence puretx can get.
 *
 * <p>Being first matters: its {@code afterCommit} runs before anyone else's, which is how puretx
 * knows that the callbacks that follow are post-commit work and must not be reported.
 *
 * <p>Its only other job is to hand the engine an elapsed time at the two moments a transaction can
 * end. Whether that elapsed time is a violation is the engine's call, not this class's.
 */
final class PuretxTransactionSynchronization implements TransactionSynchronization, Ordered {

    private final TransactionScope scope;
    private final PuretxEngine engine;

    PuretxTransactionSynchronization(final TransactionScope scope, final PuretxEngine engine) {
        this.scope = scope;
        this.engine = engine;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void beforeCommit(final boolean readOnly) {
        // The last moment at which throwing still makes sense: FAIL rolls the transaction back
        // and the exception reaches the caller.
        reportDuration(false);
    }

    @Override
    public void beforeCompletion() {
        // Reached on the rollback path too. Quiet, because a transaction that is already failing
        // does not need a second exception on top of the one that caused it.
        reportDuration(true);
    }

    @Override
    public void afterCommit() {
        scope.markPostCompletion();
    }

    @Override
    public void afterCompletion(final int status) {
        scope.markPostCompletion();
    }

    /** Reported once per transaction: whichever of the two callbacks arrives first claims it. */
    private void reportDuration(final boolean quiet) {
        if (scope.claimDurationReport()) {
            engine.reportLongTransaction(scope.elapsedMillis(), quiet);
        }
    }
}
