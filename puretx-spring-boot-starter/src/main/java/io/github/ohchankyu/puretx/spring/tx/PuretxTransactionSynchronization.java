package io.github.ohchankyu.puretx.spring.tx;

import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Registered at the start of every transaction, at the highest precedence puretx can get.
 *
 * <p>Its job is to notice the moment the transaction is over while Spring still reports it as
 * active. Being first matters: its {@code afterCommit} runs before anyone else's, which is how
 * puretx knows that the callbacks that follow are post-commit work and must not be reported, and
 * which makes it the right place to stop the clock — after the flush and the commit, before an
 * {@code AFTER_COMMIT} listener starts doing the work puretx told it to do there.
 *
 * <p>Nothing is reported here. A synchronization's {@code beforeCommit} runs before the flush
 * and the commit, so a duration taken there misses exactly the part that makes a slow
 * transaction slow, and an exception from {@code afterCompletion} is swallowed by Spring. The
 * execution listener runs after all of this and reports from there.
 */
final class PuretxTransactionSynchronization implements TransactionSynchronization, Ordered {

    private final TransactionScope scope;

    PuretxTransactionSynchronization(final TransactionScope scope) {
        this.scope = scope;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterCommit() {
        scope.markPostCompletion();
        scope.markEnded();
    }

    @Override
    public void afterCompletion(final int status) {
        scope.markPostCompletion();
        scope.markEnded();
    }
}
