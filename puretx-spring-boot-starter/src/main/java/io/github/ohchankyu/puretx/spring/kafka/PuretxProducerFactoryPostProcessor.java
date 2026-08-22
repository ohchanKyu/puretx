package io.github.ohchankyu.puretx.spring.kafka;

import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;
import org.springframework.util.function.SingletonSupplier;

/**
 * Registers puretx's producer wrapper on every {@code ProducerFactory} in the context.
 *
 * <p>Uses spring-kafka's own extension point, so the factory bean keeps its type and everything
 * built on top of it — {@code KafkaTemplate}, {@code ReplyingKafkaTemplate}, a hand-rolled producer —
 * is covered without any of them being proxied.
 */
public final class PuretxProducerFactoryPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    private final Supplier<PuretxEngine> engineSupplier;

    private final InstrumentationReport report;

    public PuretxProducerFactoryPostProcessor(final Supplier<PuretxEngine> engineSupplier,
            final InstrumentationReport report) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
        this.report = report;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (!(bean instanceof ProducerFactory<?, ?> factory)) {
            return bean;
        }
        if (factory.getPostProcessors().stream().anyMatch(PuretxProducerPostProcessor.class::isInstance)) {
            return bean;
        }
        final PuretxProducerPostProcessor added = new PuretxProducerPostProcessor(engineSupplier, factory);
        ((ProducerFactory) factory).addPostProcessor(added);

        // addPostProcessor and getPostProcessors are both interface default methods with empty
        // bodies, so a ProducerFactory that does not override them accepts the registration and
        // discards it. Counting it as instrumented would be the exact failure InstrumentationReport
        // exists to rule out: believing something is watched when nothing is.
        if (factory.getPostProcessors().contains(added)) {
            report.instrumented("Kafka producer factory");
        } else {
            log.warn("[puretx] {} does not support producer post-processors, so what it publishes "
                    + "is not seen. Only DefaultKafkaProducerFactory and subclasses can be "
                    + "instrumented.", bean.getClass().getName());
        }
        return bean;
    }

    /** Applied by the factory to every producer it creates. */
    static final class PuretxProducerPostProcessor implements ProducerPostProcessor<Object, Object> {

        private final Supplier<PuretxEngine> engineSupplier;
        private final ProducerFactory<?, ?> factory;

        PuretxProducerPostProcessor(final Supplier<PuretxEngine> engineSupplier, final ProducerFactory<?, ?> factory) {
            this.engineSupplier = engineSupplier;
            this.factory = factory;
        }

        @Override
        public Producer<Object, Object> apply(final Producer<Object, Object> producer) {
            return PuretxProducerProxy.wrap(producer, engineSupplier.get(), factory);
        }
    }
}
