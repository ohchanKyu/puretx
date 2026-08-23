package io.github.ohchankyu.puretx.spring.kafka;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Wraps a Kafka {@code Producer} so that {@code send} can be inspected before the record leaves.
 *
 * <p>Kafka's own {@code ProducerInterceptor} would be the obvious hook, but Kafka swallows anything
 * an interceptor throws — which would make {@code FAIL} mode silently useless in CI, the one place
 * it is meant to work. Wrapping the producer keeps the exception on the caller's stack.
 *
 * <p>Publishing inside a Kafka-managed transaction is not a violation and is not reported: that is
 * the transactional producer working as designed. Only a message published inside somebody else's
 * transaction — a database one, typically — is the problem, because a rollback there cannot unsend it.
 */
final class PuretxProducerProxy implements InvocationHandler {

    private final Producer<?, ?> target;

    private final PuretxEngine engine;

    private final Object producerFactory;

    private PuretxProducerProxy(final Producer<?, ?> target, final PuretxEngine engine, final Object producerFactory) {
        this.target = target;
        this.engine = engine;
        this.producerFactory = producerFactory;
    }

    @SuppressWarnings("unchecked")
    static <K, V> Producer<K, V> wrap(final Producer<K, V> target, final PuretxEngine engine, final Object producerFactory) {
        return (Producer<K, V>) Proxy.newProxyInstance(
                Producer.class.getClassLoader(),
                new Class<?>[] {Producer.class},
                new PuretxProducerProxy(target, engine, producerFactory));
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if ("send".equals(method.getName())
                && args.length > 0
                && args[0] instanceof ProducerRecord<?, ?> record
        ) {
            inspect(record);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    private void inspect(final ProducerRecord<?, ?> record) {
        if (!engine.isWatching(ViolationType.MESSAGE_PUBLISH) || isKafkaManagedTransaction()) {
            return;
        }
        engine.report(ViolationType.MESSAGE_PUBLISH, () -> "Kafka send -> topic '" + record.topic() + "'");
    }

    /** True when Spring has bound this producer to the current transaction, i.e. it is a Kafka transaction. */
    private boolean isKafkaManagedTransaction() {
        return TransactionSynchronizationManager.getResource(producerFactory) != null;
    }
}
