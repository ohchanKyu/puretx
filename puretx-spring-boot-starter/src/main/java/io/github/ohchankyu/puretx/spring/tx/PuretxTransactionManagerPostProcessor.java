package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
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

    public PuretxTransactionManagerPostProcessor(final Supplier<PuretxEngine> engineSupplier,
            final InstrumentationReport report) {
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
                engineSupplier, manager.getClass().getSimpleName()));
        manager.setTransactionExecutionListeners(listeners);
        // setTransactionExecutionListeners replaces the list wholesale, so anything that calls it
        // later drops puretx's listener without telling anyone.
        report.instrumented("transaction manager", () -> manager.getTransactionExecutionListeners().stream()
                .anyMatch(PuretxTransactionExecutionListener.class::isInstance));
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
