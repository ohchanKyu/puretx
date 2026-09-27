package io.github.ohchankyu.puretx.spring;

import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.tx.PuretxTransactionManagerPostProcessor;
import io.github.ohchankyu.puretx.spring.tx.TransactionScopeManager;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Points the static {@code Puretx} facade at this context's engine, whichever bean that is.
 *
 * <p>Done here rather than inside the engine's own bean method so that an application which
 * defines its own {@code PuretxEngine} bean — which makes the auto-configured one back off — still
 * gets a working {@code Puretx.watch}. Left in the bean method, that application's calls went to
 * whatever engine some other context had installed, or to none.
 *
 * <p>The resolver it installs answers, for the current thread: the engine of the scope that is
 * open, if puretx saw the transaction begin; this engine, if a transaction is open on a resource
 * one of this context's managers owns, which is how a manager built by hand on the context's
 * data source is recognised; a disabled engine, if a transaction is open on anything else, which
 * is another context's transaction — one with {@code puretx.enabled=false}, typically — and must
 * not be reported here; and nothing outside any transaction, where the static engine is fine.
 */
final class PuretxFacadeInstaller implements SmartInitializingSingleton {

    private final PuretxEngine engine;

    private final @Nullable PuretxTransactionManagerPostProcessor managers;

    PuretxFacadeInstaller(final PuretxEngine engine, final @Nullable PuretxTransactionManagerPostProcessor managers) {
        this.engine = engine;
        this.managers = managers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Puretx.setEngine(engine);
        Puretx.setScopedEngine(this::resolve);
    }

    private @Nullable PuretxEngine resolve() {
        final PuretxEngine scoped = TransactionScopeManager.currentEngine();
        if (scoped != null) {
            return scoped;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        return managers != null && managers.ownsCurrentTransaction() ? engine : PuretxEngine.disabled();
    }
}
