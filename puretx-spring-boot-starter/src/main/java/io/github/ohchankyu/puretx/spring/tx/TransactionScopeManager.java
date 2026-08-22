package io.github.ohchankyu.puretx.spring.tx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The per-thread stack of open transaction scopes.
 *
 * <p>A stack rather than a single slot because {@code REQUIRES_NEW} suspends the outer transaction
 * and starts an inner one on the same thread; when the inner one completes the outer must come back.
 */
public final class TransactionScopeManager {

    private static final ThreadLocal<Deque<TransactionScope>> SCOPES = new ThreadLocal<>();

    private TransactionScopeManager() {
    }

    static void push(final TransactionScope scope) {
        Deque<TransactionScope> stack = SCOPES.get();
        if (stack == null) {
            stack = new ArrayDeque<>(4);
            SCOPES.set(stack);
        } else {
            // Threads are pooled and reused. If a transaction ever finished without its completion
            // callback reaching us, its scope would sit here for the life of the thread.
            stack.removeIf(TransactionScope::isFinished);
        }
        stack.push(scope);
    }

    /** The innermost open transaction on this thread, or {@code null}. */
    public static TransactionScope current() {
        Deque<TransactionScope> stack = SCOPES.get();
        return stack == null ? null : stack.peek();
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
