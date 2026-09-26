package io.github.ohchankyu.puretx.spring.tx;

import io.github.ohchankyu.puretx.spring.kafka.KafkaResourceKey;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.util.ClassUtils;

/**
 * What a transaction manager binds to the thread while one of its transactions is open.
 *
 * <p>Spring's own managers say so through {@link ResourceTransactionManager}: a data source, an
 * entity manager factory, a connection factory. {@code KafkaTransactionManager} does not
 * implement it and is asked directly, through a class that is only loaded when Spring Kafka is
 * on the classpath.
 */
final class TransactionResourceKeys {

    private static final boolean KAFKA_PRESENT = ClassUtils.isPresent(
            "org.springframework.kafka.transaction.KafkaAwareTransactionManager",
            TransactionResourceKeys.class.getClassLoader());

    private TransactionResourceKeys() {}

    static @Nullable Object of(final PlatformTransactionManager manager) {
        if (manager instanceof ResourceTransactionManager resourceManager) {
            return resourceManager.getResourceFactory();
        }
        if (KAFKA_PRESENT) {
            return KafkaResourceKey.of(manager);
        }
        return null;
    }
}
