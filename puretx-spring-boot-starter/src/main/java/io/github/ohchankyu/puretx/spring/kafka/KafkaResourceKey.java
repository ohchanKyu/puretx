package io.github.ohchankyu.puretx.spring.kafka;

import org.jspecify.annotations.Nullable;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** The producer factory a {@code KafkaTransactionManager} binds for the length of a transaction. */
public final class KafkaResourceKey {

    private KafkaResourceKey() {}

    public static @Nullable Object of(final PlatformTransactionManager manager) {
        return manager instanceof KafkaAwareTransactionManager<?, ?> kafka ? kafka.getProducerFactory() : null;
    }
}
