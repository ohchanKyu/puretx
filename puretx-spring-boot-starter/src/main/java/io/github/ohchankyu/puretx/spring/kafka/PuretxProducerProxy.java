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
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.core.KafkaResourceHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ReflectionUtils;

/**
 * Wraps a Kafka {@code Producer} so that {@code send} can be inspected before the record leaves.
 *
 * <p>Kafka's own {@code ProducerInterceptor} would be the obvious hook, but Kafka swallows anything
 * an interceptor throws — which would make {@code FAIL} mode silently useless in CI, the one place
 * it is meant to work. Wrapping the producer keeps the exception on the caller's stack.
 *
 * <p>A send is exempt when it is part of a Kafka transaction that Spring has tied to the
 * surrounding transaction: a {@code KafkaTransactionManager} transaction, or a transactional
 * {@code KafkaTemplate} joined to a database transaction, which commits after the database does
 * so that a rollback takes the message back. Two things have to hold for that. The producer is
 * inside a Kafka transaction, which it knows itself — it saw {@code beginTransaction} and not yet
 * the commit or abort — and Spring has bound a {@code KafkaResourceHolder} to the thread, which
 * is how it ties the Kafka transaction to its own. The first alone is not enough:
 * {@code KafkaTemplate.executeInTransaction} runs a local Kafka transaction that commits on its
 * own, before the database does, and a rollback there cannot unsend it. That one is reported.
 *
 * <p>The holder is looked for by type rather than by factory, because
 * {@code new KafkaTemplate(factory, overrides)} copies the factory and binds the copy, while the
 * post-processor that made this proxy only knew the original. And the holder has to be wrapping
 * <em>this</em> producer: with two factories, one in a Spring-managed Kafka transaction and the
 * other running a local one, the local one commits on its own and must still be reported. The
 * holder's producer is the factory's {@code CloseSafeProducer} around this proxy, whose delegate
 * accessor is package-private; it is read reflectively, and if that is ever impossible the
 * check falls back to the holder's presence alone.
 */
final class PuretxProducerProxy implements InvocationHandler {

    private static final String CLOSE_SAFE_PRODUCER =
            "org.springframework.kafka.core.DefaultKafkaProducerFactory$CloseSafeProducer";

    private final Producer<?, ?> target;

    private final PuretxEngine engine;

    /** A transactional producer is driven by one thread at a time, but not always the same one. */
    private volatile boolean inTransaction;

    private PuretxProducerProxy(final Producer<?, ?> target, final PuretxEngine engine) {
        this.target = target;
        this.engine = engine;
    }

    @SuppressWarnings("unchecked")
    static <K, V> Producer<K, V> wrap(final Producer<K, V> target, final PuretxEngine engine) {
        return (Producer<K, V>) Proxy.newProxyInstance(
                Producer.class.getClassLoader(),
                new Class<?>[] {Producer.class},
                new PuretxProducerProxy(target, engine));
    }

    @Override
    public @Nullable Object invoke(final Object proxy, final Method method, final Object @Nullable [] args) throws Throwable {
        switch (method.getName()) {
            case "send" -> {
                if (args != null && args.length > 0 && args[0] instanceof ProducerRecord<?, ?> record) {
                    return send(proxy, method, args, record);
                }
            }
            case "beginTransaction" -> {
                final Object result = invokeTarget(method, args);
                inTransaction = true;
                return result;
            }
            case "commitTransaction", "abortTransaction" -> {
                try {
                    return invokeTarget(method, args);
                } finally {
                    inTransaction = false;
                }
            }
            default -> {
            }
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
     * one violation a rollback cannot take back. What it does not include is the broker's
     * acknowledgement, which arrives on the producer's own thread after {@code send} returned.
     */
    private @Nullable Object send(final Object proxy, final Method method, final Object[] args,
            final ProducerRecord<?, ?> record) throws Throwable {
        final Detection detection = detect(proxy, record);
        if (detection == null) {
            return invokeTarget(method, args);
        }
        try {
            return invokeTarget(method, args);
        } finally {
            engine.finish(detection);
        }
    }

    private @Nullable Object invokeTarget(final Method method, final Object @Nullable [] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    private @Nullable Detection detect(final Object proxy, final ProducerRecord<?, ?> record) {
        if (!engine.isWatching(ViolationType.MESSAGE_PUBLISH) || isSpringManagedKafkaTransaction(proxy)) {
            return null;
        }
        return engine.start(ViolationType.MESSAGE_PUBLISH,
                () -> "Kafka send -> topic '" + record.topic() + "'");
    }

    private boolean isSpringManagedKafkaTransaction(final Object proxy) {
        return inTransaction && TransactionSynchronizationManager.getResourceMap().values().stream()
                .anyMatch(resource -> resource instanceof KafkaResourceHolder<?, ?> holder && wraps(holder, proxy));
    }

    /**
     * Whether {@code holder} is Spring's binding of this very producer, as far as can be told.
     *
     * <p>{@code DefaultKafkaProducerFactory} hands Spring a {@code CloseSafeProducer} around the
     * post-processed producer. That class is protected and its delegate accessor package-private,
     * so both are reached by name. A holder around another puretx proxy is plainly not this one.
     * Any other wrapper is unknown, and an unknown wrapper is taken to be Spring's: a false
     * positive on the pattern the README recommends is the worse error.
     */
    private static boolean wraps(final KafkaResourceHolder<?, ?> holder, final Object proxy) {
        final Producer<?, ?> held = holder.getProducer();
        if (held == proxy) {
            return true;
        }
        if (Proxy.isProxyClass(held.getClass()) && Proxy.getInvocationHandler(held) instanceof PuretxProducerProxy) {
            return false;
        }
        if (!CLOSE_SAFE_PRODUCER.equals(held.getClass().getName())) {
            return true;
        }
        final Method delegate = ReflectionUtils.findMethod(held.getClass(), "getDelegate");
        if (delegate == null) {
            return true;
        }
        try {
            ReflectionUtils.makeAccessible(delegate);
            return ReflectionUtils.invokeMethod(delegate, held) == proxy;
        } catch (RuntimeException inaccessible) {
            return true;
        }
    }
}
