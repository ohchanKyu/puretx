package io.github.ohchankyu.puretx.spring.kafka;

import io.github.ohchankyu.puretx.Detection;
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
            return send(method, args, record);
        }
        return invokeTarget(method, args);
    }

    /**
     * Times the send as well as reporting it.
     *
     * <p>{@code KafkaProducer.send} buffers and returns, but it blocks for up to
     * {@code max.block.ms} while it waits for metadata or for room in the buffer — real time, held
     * with the transaction open. Reporting it without a duration left the transaction summary
     * saying a publish cost 0% of the transaction, which reads as "nothing to see here" for the
     * one violation a rollback cannot take back.
     */
    private Object send(final Method method, final Object[] args, final ProducerRecord<?, ?> record)
            throws Throwable {
        final Detection detection = detect(record);
        if (detection == null) {
            return invokeTarget(method, args);
        }
        try {
            return invokeTarget(method, args);
        } finally {
            engine.finish(detection);
        }
    }

    private Object invokeTarget(final Method method, final Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    private Detection detect(final ProducerRecord<?, ?> record) {
        if (!engine.isWatching(ViolationType.MESSAGE_PUBLISH) || isKafkaManagedTransaction()) {
            return null;
        }
        return engine.start(ViolationType.MESSAGE_PUBLISH,
                () -> "Kafka send -> topic '" + record.topic() + "'");
    }

    /** True when Spring has bound this producer to the current transaction, i.e. it is a Kafka transaction. */
    private boolean isKafkaManagedTransaction() {
        return TransactionSynchronizationManager.getResource(producerFactory) != null;
    }
}
