package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionSummary;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

    private final AtomicBoolean summaryReported = new AtomicBoolean();

    private volatile boolean ended;

    private volatile long endedMillis = -1;

    /** Written from more than one thread: a reactive client records on the thread it completed on. */
    private final AtomicInteger callCount = new AtomicInteger();

    private final AtomicLong callMillis = new AtomicLong();

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

    /**
     * Adds one reported call to what this transaction spent its life on.
     *
     * <p>Duration first, count second. {@link #summarise()} reads count and then millis, so a
     * reader that sees the call counted has necessarily seen its duration too — the other order
     * can be caught mid-write and report "1 call, 0ms, 0%".
     */
    void recordCall(final long durationMillis) {
        if (durationMillis > 0) {
            callMillis.addAndGet(durationMillis);
        }
        callCount.incrementAndGet();
    }

    /**
     * Marks the transaction as over, so a call recorded after this knows it arrived late.
     *
     * <p>Freezes how long it was held, because a call recorded after the commit summarises from
     * its own thread and would otherwise measure the transaction as lasting until then.
     */
    void markEnded() {
        this.endedMillis = elapsedMillis();
        this.ended = true;
    }

    boolean hasEnded() {
        return ended;
    }

    /**
     * The summary, or {@code null} when there is nothing to summarise or it has already been given.
     *
     * <p>Claimed atomically because it can be asked for from two places: when the transaction ends,
     * and again by a call that was recorded after that — a reactive client completes on its own
     * thread and can land after the commit has been and gone.
     */
    TransactionSummary summarise() {
        final int calls = callCount.get();
        if (calls == 0 || !summaryReported.compareAndSet(false, true)) {
            return null;
        }
        final long held = endedMillis >= 0 ? endedMillis : elapsedMillis();
        return new TransactionSummary(name, held, calls, callMillis.get());
    }

    boolean claimDurationReport() {
        if (durationReported) {
            return false;
        }
        durationReported = true;
        return true;
    }

    public TransactionInfo snapshot() {
        return new TransactionInfo(name, elapsedMillis(), readOnly, testManaged, managerType, this);
    }
}
