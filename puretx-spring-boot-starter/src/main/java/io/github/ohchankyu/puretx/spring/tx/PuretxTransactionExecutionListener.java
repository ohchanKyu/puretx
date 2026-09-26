package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.TransactionSummary;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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
 * data stays, because there is nothing left to abort by then.
 *
 * <p>One instance exists per transaction manager, so a violation can say which one was in charge.
 */
public final class PuretxTransactionExecutionListener implements TransactionExecutionListener {

    private final Supplier<PuretxEngine> engineSupplier;

    private final String managerType;

    public PuretxTransactionExecutionListener(final Supplier<PuretxEngine> engineSupplier, final String managerType) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
        this.managerType = managerType;
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
                transaction.getTransactionName(),
                transaction.isReadOnly(),
                TestTransactionDetector.isTestManaged(),
                managerType);
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
            return;
        }
        scope.markCompleted();
        scope.markEnded();
        TransactionScopeManager.pop(scope);
        final PuretxEngine engine = engineSupplier.get();
        try {
            engine.reportLongTransaction(scope.snapshot(), scope.heldMillis(), quiet);
        } finally {
            final TransactionSummary summary = scope.summarise();
            if (summary != null) {
                engine.reportTransactionSummary(summary);
            }
        }
    }
}
