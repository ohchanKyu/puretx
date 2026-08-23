package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.TransactionInfo;
import lombok.Getter;
import org.springframework.transaction.TransactionExecution;

/**
 * What puretx knows about one physical transaction, from begin to completion.
 *
 * <p>Confined to the thread that started the transaction, and popped when that transaction
 * completes — including the {@code REQUIRES_NEW} case, which pushes a scope of its own.
 */
public final class TransactionScope {

    private final TransactionExecution execution;

    private final String name;

    private final long startNanos;

    private final boolean readOnly;

    private final boolean testManaged;

    private final String managerType;

    /**
     * True once the transaction has committed (or rolled back) and Spring is running the
     * {@code afterCommit} / {@code afterCompletion} callbacks. The transaction still looks active
     * to {@code TransactionSynchronizationManager} at that point, which is the single biggest
     * source of false positives — deferring work to {@code afterCommit} is the fix puretx recommends.
     */
    @Getter
    private boolean postCompletion;

    private boolean durationReported;

    @Getter
    private boolean completed;

    TransactionScope(
        final TransactionExecution execution,
        final String name,
        final boolean readOnly,
        final boolean testManaged,
        final String managerType
    ) {
        this.execution = execution;
        this.name = name;
        this.readOnly = readOnly;
        this.testManaged = testManaged;
        this.managerType = managerType;
        this.startNanos = System.nanoTime();
    }

    public TransactionExecution execution() {
        return execution;
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    void markPostCompletion() {
        this.postCompletion = true;
    }

    /** True once this scope, or the transaction behind it, is done with. */
    boolean isFinished() {
        return completed || execution.isCompleted();
    }

    void markCompleted() {
        this.completed = true;
    }

    boolean claimDurationReport() {
        if (durationReported) {
            return false;
        }
        durationReported = true;
        return true;
    }

    public TransactionInfo snapshot() {
        return new TransactionInfo(name, elapsedMillis(), readOnly, testManaged, managerType);
    }
}
