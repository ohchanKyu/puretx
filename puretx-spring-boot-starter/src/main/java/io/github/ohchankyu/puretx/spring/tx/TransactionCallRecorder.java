package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.TransactionSummary;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationListener;
import java.util.function.Supplier;

/**
 * Attributes each reported call back to the transaction it interrupted.
 *
 * <p>Not by thread: a reactive client records on whichever thread its exchange completed on, and
 * the scope stack is thread-confined. The scope travels with the violation instead, as the opaque
 * {@code source} the probe put on {@code TransactionInfo}.
 */
final class TransactionCallRecorder implements ViolationListener {

    private final Supplier<PuretxEngine> engine;

    TransactionCallRecorder(final Supplier<PuretxEngine> engine) {
        this.engine = engine;
    }

    @Override
    public void onViolation(final Violation violation) {
        // A long transaction is the transaction, not something that happened inside it.
        if (violation.type().isTransactionItself()) {
            return;
        }
        if (!(violation.transaction().source() instanceof TransactionScope scope)) {
            return;
        }
        scope.recordCall(violation.durationMillis());
        if (scope.hasEnded()) {
            // The transaction finished before this call was recorded, so the summary it would have
            // given was empty and was never sent. Send it now, from here.
            final TransactionSummary summary = scope.summarise();
            if (summary != null) {
                engine.get().reportTransactionSummary(summary);
            }
        }
    }
}
