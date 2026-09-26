package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.ImpureTransactionException;
import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxMode;
import io.github.ohchankyu.puretx.TransactionSummary;
import io.github.ohchankyu.puretx.Violation;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.SingletonSupplier;

/**
 * Hooks the beginning and the end of every transaction a transaction manager runs.
 *
 * <p>Spring has offered {@code TransactionExecutionListener} since Framework 6.1, which is why
 * puretx needs no proxy around the transaction manager: it is handed the transaction's name, its
 * read-only flag and — crucially — the exact moment it opened. "You made an HTTP call 1.2 seconds
 * into this transaction" is only possible because of that last one.
 *
 * <p>The end is reported here rather than from a synchronization, for two reasons. This runs
 * after the commit itself and after every synchronization callback, so an exception thrown here
 * reaches the caller, and it runs whether or not the manager allows synchronizations at all,
 * which {@code KafkaTransactionManager} by default does not. The clock itself is stopped earlier,
 * by the synchronization's {@code afterCommit} when there is one: that is after the flush and the
 * commit, the slow parts of a slow transaction, but before the {@code AFTER_COMMIT} work that
 * puretx itself recommends, which must not count against the transaction it followed.
 *
 * <p>In {@code FAIL} mode a transaction held too long throws from {@link #afterCommit}. Spring
 * propagates that to the caller with the transaction already committed: the test fails and the
 * data stays, because there is nothing left to abort by then. When that transaction was an inner
 * one — {@code REQUIRES_NEW} — throwing there would reach the outer method and roll the outer
 * transaction back while the inner stays committed, a half-written state that no test wants.
 * So an inner failure is handed to the enclosing scope and thrown once the outermost transaction
 * has committed too — unless the enclosing transaction is the one Spring's test framework opened
 * around the test, which always rolls back and would swallow it; then it is thrown at once.
 *
 * <p>One instance exists per transaction manager, so a violation can say which one was in charge.
 */
public final class PuretxTransactionExecutionListener implements TransactionExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    private final Supplier<PuretxEngine> engineSupplier;

    private final String managerType;

    private final Supplier<@Nullable Object> resourceKey;

    /**
     * @param resourceKey what the manager binds to the thread for the length of a transaction,
     *                    or {@code null} when it binds nothing; asked for at each begin, because
     *                    a manager may not know it before its own initialisation has finished
     */
    public PuretxTransactionExecutionListener(final Supplier<PuretxEngine> engineSupplier, final String managerType,
            final Supplier<@Nullable Object> resourceKey) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
        this.managerType = managerType;
        this.resourceKey = resourceKey;
    }

    @Override
    public void afterBegin(final TransactionExecution transaction, final @Nullable Throwable beginFailure) {
        PuretxEngine engine = engineSupplier.get();
        if (beginFailure != null || engine.settings().idle()) {
            return;
        }
        if (!transaction.isNewTransaction()) {
            return;
        }
        TransactionScope scope = new TransactionScope(
                transaction,
                engine,
                transaction.getTransactionName(),
                transaction.isReadOnly(),
                TestTransactionDetector.isTestManaged(),
                managerType,
                resourceKey.get());
        TransactionScopeManager.push(scope);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new PuretxTransactionSynchronization(scope));
            scope.markSynchronised();
        }
    }

    @Override
    public void afterCommit(final TransactionExecution transaction, final @Nullable Throwable commitFailure) {
        end(transaction, commitFailure != null);
    }

    @Override
    public void afterRollback(final TransactionExecution transaction, final @Nullable Throwable rollbackFailure) {
        end(transaction, true);
    }

    /**
     * Drops the scope, reports the transaction's length, then summarises it.
     *
     * <p>The scope comes off the stack first, so that an exception from the duration report
     * leaves nothing behind. The summary goes out even then: it explains violations already
     * reported, and a test that is failing anyway still deserves the number.
     *
     * @param quiet do not throw in {@code FAIL} mode: a rollback, or a commit that failed, has an
     *              exception of its own on the way to the caller
     */
    private void end(final TransactionExecution transaction, final boolean quiet) {
        if (!transaction.isNewTransaction()) {
            return;
        }
        final TransactionScope scope = TransactionScopeManager.current();
        if (scope == null || scope.execution() != transaction || scope.isCompleted()) {
            if (log.isDebugEnabled()) {
                log.debug("[puretx] {} ended a transaction that is not the innermost one puretx knows about; "
                        + "it will not be timed or summarised. Two transaction managers driven by hand and "
                        + "completed out of order look like this.", managerType);
            }
            return;
        }
        scope.markCompleted();
        scope.markEnded();
        TransactionScopeManager.pop(scope);
        final PuretxEngine engine = engineSupplier.get();
        try {
            final Violation own = engine.reportLongTransaction(scope.snapshot(), scope.heldMillis());
            final Violation failure = own != null ? own : scope.deferredFailure();
            if (failure != null && !quiet && engine.settings().mode() == PuretxMode.FAIL) {
                final TransactionScope outer = TransactionScopeManager.current();
                if (outer != null && !outer.isTestManaged()) {
                    outer.deferFailure(failure);
                } else {
                    throw new ImpureTransactionException(failure);
                }
            }
        } finally {
            final TransactionSummary summary = scope.summarise();
            if (summary != null) {
                engine.reportTransactionSummary(summary);
            }
        }
    }
}
