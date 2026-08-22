package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Answers "is this thread inside a transaction that puretx should police?" for Spring.
 *
 * <p>The interesting part is what it refuses to report. {@code isActualTransactionActive()} is still
 * true while Spring runs {@code afterCommit} callbacks and {@code @TransactionalEventListener(AFTER_COMMIT)}
 * handlers — the very place puretx tells people to move their HTTP calls and their message publishing to.
 * Flagging that would make the library useless, so the scope tracks the post-commit window explicitly.
 */
public final class SpringTransactionProbe implements TransactionProbe {

    @Override
    public TransactionInfo currentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        TransactionScope scope = TransactionScopeManager.current();
        if (scope != null) {
            return scope.isPostCompletion() ? null : scope.snapshot();
        }
        // A transaction manager puretx could not instrument (not an AbstractPlatformTransactionManager).
        // Still worth reporting, just without the timing.
        return new TransactionInfo(
                TransactionSynchronizationManager.getCurrentTransactionName(),
                -1,
                TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                false,
                "");
    }
}
