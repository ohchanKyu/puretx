package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The per-thread stack of open transaction scopes.
 *
 * <p>A stack rather than a single slot because {@code REQUIRES_NEW} suspends the outer transaction
 * and starts an inner one on the same thread; when the inner one completes the outer must come back.
 */
public final class TransactionScopeManager {

    private static final ThreadLocal<Deque<TransactionScope>> SCOPES = new ThreadLocal<>();

    private TransactionScopeManager() {}

    /** The listener that attributes reported calls to the transaction they interrupted. */
    public static ViolationListener callRecorder(final Supplier<PuretxEngine> engine) {
        return new TransactionCallRecorder(engine);
    }

    static void push(final TransactionScope scope) {
        Deque<TransactionScope> stack = SCOPES.get();
        if (stack == null) {
            stack = new ArrayDeque<>(4);
            SCOPES.set(stack);
        } else {
            stack.removeIf(TransactionScope::isFinished);
        }
        stack.push(scope);
    }

    /** The innermost open transaction on this thread, or {@code null}. */
    public static @Nullable TransactionScope current() {
        Deque<TransactionScope> stack = SCOPES.get();
        return stack == null ? null : stack.peek();
    }

    /**
     * The engine that opened the innermost transaction on this thread; a disabled engine when the
     * thread is in a Spring transaction puretx did not see begin; {@code null} outside any.
     *
     * <p>What {@code Puretx.watch} and {@code Puretx.violations()} resolve through, so that inside
     * a transaction they use the engine of the context that owns it rather than whichever
     * context started last. A transaction with no scope was opened by a manager puretx did not
     * instrument — a context with {@code puretx.enabled=false}, most likely, sharing the JVM
     * with one that is on. Falling back to the static engine there would report that context's
     * calls into another context's store, so it falls back to nothing instead.
     */
    public static @Nullable PuretxEngine currentEngine() {
        final TransactionScope scope = current();
        if (scope != null && !scope.isFinished()) {
            return scope.engine();
        }
        return TransactionSynchronizationManager.isActualTransactionActive() ? PuretxEngine.disabled() : null;
    }

    static void pop(final TransactionScope scope) {
        Deque<TransactionScope> stack = SCOPES.get();
        if (stack == null) {
            return;
        }
        stack.remove(scope);
        if (stack.isEmpty()) {
            SCOPES.remove();
        }
    }

}
