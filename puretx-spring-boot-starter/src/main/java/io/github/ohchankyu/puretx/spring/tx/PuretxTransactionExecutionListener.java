package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import java.util.function.Supplier;
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
    public void afterBegin(final TransactionExecution transaction, final Throwable beginFailure) {
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
            TransactionSynchronizationManager.registerSynchronization(
                    new PuretxTransactionSynchronization(scope, engine));
        }
    }

    @Override
    public void afterCommit(final TransactionExecution transaction, final Throwable commitFailure) {
        complete(transaction);
    }

    @Override
    public void afterRollback(final TransactionExecution transaction, final Throwable rollbackFailure) {
        complete(transaction);
    }

    /**
     * Runs after every synchronization callback for this transaction has finished, which makes it
     * the only safe place to drop the scope — post-commit callbacks still need to see it to know
     * they are post-commit.
     */
    private void complete(final TransactionExecution transaction) {
        if (!transaction.isNewTransaction()) {
            return;
        }
        TransactionScope scope = TransactionScopeManager.current();
        if (scope == null || scope.execution() != transaction || scope.isCompleted()) {
            return;
        }
        scope.markCompleted();
        TransactionScopeManager.pop(scope);
    }
}
