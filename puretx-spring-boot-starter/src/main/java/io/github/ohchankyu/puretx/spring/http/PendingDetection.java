package io.github.ohchankyu.puretx.spring.http;

import io.github.ohchankyu.puretx.Detection;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.tx.TransactionScope;
import io.github.ohchankyu.puretx.spring.tx.TransactionScopeManager;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * A detection whose end depends on the caller, with the transaction's end as a deadline.
 *
 * <p>An HTTP detection finishes when the caller closes the response or reads its body to the
 * end. A caller that does neither — {@code RestClient.exchange(fn, false)} and then no
 * {@code close()} — would never finish it, and a call that plainly happened inside the
 * transaction would go unreported. Registering with the transaction's scope gives it a
 * deadline: when the transaction ends, whatever is still pending is finished then, timed up
 * to that end, which is as long as the connection was demonstrably held. Outside any scope
 * there is no deadline and the caller's close is the only end.
 */
final class PendingDetection implements TransactionScope.Pending {

    private final PuretxEngine engine;

    private final Detection detection;

    private final @Nullable TransactionScope scope;

    private final AtomicBoolean finished = new AtomicBoolean();

    PendingDetection(final PuretxEngine engine, final Detection detection) {
        this.engine = engine;
        this.detection = detection;
        this.scope = TransactionScopeManager.current();
        if (scope != null) {
            scope.registerPending(this);
        }
    }

    @Override
    public void finish() {
        if (finished.compareAndSet(false, true)) {
            if (scope != null) {
                scope.unregisterPending(this);
            }
            engine.finish(detection);
        }
    }
}
