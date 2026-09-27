package io.github.ohchankyu.puretx.spring;

import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.tx.TransactionScopeManager;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Points the static {@code Puretx} facade at this context's engine, whichever bean that is.
 *
 * <p>Done here rather than inside the engine's own bean method so that an application which
 * defines its own {@code PuretxEngine} bean — which makes the auto-configured one back off — still
 * gets a working {@code Puretx.watch}. Left in the bean method, that application's calls went to
 * whatever engine some other context had installed, or to none.
 */
final class PuretxFacadeInstaller implements SmartInitializingSingleton {

    private final PuretxEngine engine;

    PuretxFacadeInstaller(final PuretxEngine engine) {
        this.engine = engine;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Puretx.setEngine(engine);
        Puretx.setScopedEngine(TransactionScopeManager::currentEngine);
    }
}
