package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.SingletonSupplier;

/**
 * Attaches puretx's execution listener to every transaction manager in the context.
 *
 * <p>Deliberately not a proxy. The bean keeps its identity and its concrete type, so code that
 * autowires {@code JpaTransactionManager} or casts to it keeps working — a library that quietly
 * swaps out the transaction manager is a library that eventually breaks someone's application.
 *
 * <p>Reactive transaction managers are not covered; puretx's detection is thread-bound.
 */
public final class PuretxTransactionManagerPostProcessor implements BeanPostProcessor, Ordered {

    private final Supplier<PuretxEngine> engineSupplier;

    private final InstrumentationReport report;

    private final List<AbstractPlatformTransactionManager> instrumented = new CopyOnWriteArrayList<>();

    public PuretxTransactionManagerPostProcessor(final Supplier<PuretxEngine> engineSupplier, final InstrumentationReport report) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
        this.report = report;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (!(bean instanceof AbstractPlatformTransactionManager manager)) {
            return bean;
        }
        for (TransactionExecutionListener listener : manager.getTransactionExecutionListeners()) {
            if (listener instanceof PuretxTransactionExecutionListener) {
                return bean;
            }
        }
        List<TransactionExecutionListener> listeners =
                new ArrayList<>(manager.getTransactionExecutionListeners());
        listeners.add(new PuretxTransactionExecutionListener(
                engineSupplier, manager.getClass().getSimpleName(), () -> TransactionResourceKeys.of(manager)));
        manager.setTransactionExecutionListeners(listeners);
        instrumented.add(manager);
        report.instrumented("transaction manager", () -> manager.getTransactionExecutionListeners().stream()
                .anyMatch(PuretxTransactionExecutionListener.class::isInstance));
        return bean;
    }

    /**
     * Whether the transaction open on this thread runs on a resource one of this context's
     * managers owns: its data source, entity manager factory or producer factory is bound.
     *
     * <p>Asked when a transaction is active but no scope was pushed, which means a manager
     * puretx did not instrument opened it. One built by hand on the same data source as the
     * bean — {@code new TransactionTemplate(new JpaTransactionManager(emf))} — is this context's
     * business and its calls should be reported here. A transaction on some other context's
     * resources, the usual case being a context with {@code puretx.enabled=false} sharing the
     * JVM, is not, and reporting it here would put another context's calls in this store.
     */
    public boolean ownsCurrentTransaction() {
        for (final AbstractPlatformTransactionManager manager : instrumented) {
            final Object key = TransactionResourceKeys.of(manager);
            if (key != null && TransactionSynchronizationManager.hasResource(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
