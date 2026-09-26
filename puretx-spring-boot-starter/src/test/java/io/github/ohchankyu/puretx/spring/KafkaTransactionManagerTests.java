package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxSettings;
import io.github.ohchankyu.puretx.TransactionSummary;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationListener;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.tx.PuretxTransactionManagerPostProcessor;
import io.github.ohchankyu.puretx.spring.tx.SpringTransactionProbe;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code KafkaTransactionManager} runs with {@code SYNCHRONIZATION_NEVER} by default, so Spring
 * never marks the thread as having an actual transaction. puretx has to see it anyway.
 */
class KafkaTransactionManagerTests {

    private final List<TransactionSummary> summaries = new ArrayList<>();

    @Test
    @DisplayName("an HTTP call inside a Kafka transaction is reported, and names the manager")
    void seesWorkInsideAKafkaTransaction() {
        final PuretxEngine engine = engine(Duration.ofSeconds(3));
        final TransactionTemplate kafka = new TransactionTemplate(instrumented(engine));

        kafka.executeWithoutResult(status ->
                engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com"));

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.transaction().managerType()).isEqualTo("KafkaTransactionManager");
        });
    }

    @Test
    @DisplayName("a Kafka transaction held too long is reported and summarised without any synchronization")
    void timesAKafkaTransaction() {
        final PuretxEngine engine = engine(Duration.ofMillis(50));
        final TransactionTemplate kafka = new TransactionTemplate(instrumented(engine));

        kafka.executeWithoutResult(status -> {
            engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");
            sleep(120);
        });

        assertThat(engine.store().all()).extracting(Violation::type)
                .containsExactly(ViolationType.HTTP_CALL, ViolationType.LONG_TRANSACTION);
        assertThat(summaries).singleElement().satisfies(summary ->
                assertThat(summary.transactionMillis()).isGreaterThanOrEqualTo(120));
    }

    @Test
    @DisplayName("a suspended Kafka transaction is not reported, even with open-in-view holding a resource")
    void staysQuietWhileSuspendedDespiteUnrelatedResources() {
        final PuretxEngine engine = engine(Duration.ofSeconds(3));
        final KafkaTransactionManager<String, String> manager = instrumented(engine);
        final TransactionTemplate kafka = new TransactionTemplate(manager);
        final TransactionTemplate suspended = new TransactionTemplate(manager);
        suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        final Object entityManagerFactoryStandIn = new Object();
        TransactionSynchronizationManager.bindResource(entityManagerFactoryStandIn, new Object());
        try {
            kafka.executeWithoutResult(status -> {
                suspended.executeWithoutResult(inner ->
                        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com/suspended"));
                engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com/resumed");
            });
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactoryStandIn);
        }

        assertThat(engine.store().all()).extracting(Violation::summary)
                .containsExactly("HTTP GET https://example.com/resumed");
    }

    @Test
    @DisplayName("once the Kafka transaction is over, the thread is clean")
    void leavesNothingBehind() {
        final PuretxEngine engine = engine(Duration.ofSeconds(3));
        final TransactionTemplate kafka = new TransactionTemplate(instrumented(engine));

        kafka.executeWithoutResult(status -> { });
        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

        assertThat(engine.store().all()).isEmpty();
    }

    private PuretxEngine engine(final Duration maxDuration) {
        final PuretxEngine engine = new PuretxEngine(
                PuretxSettings.builder().maxDuration(maxDuration).build(), new SpringTransactionProbe());
        engine.addListener(io.github.ohchankyu.puretx.spring.tx.TransactionScopeManager.callRecorder(() -> engine));
        engine.addListener(new ViolationListener() {
            @Override
            public void onViolation(final Violation violation) {
            }

            @Override
            public void onTransactionSummary(final TransactionSummary summary) {
                summaries.add(summary);
            }
        });
        return engine;
    }

    private static KafkaTransactionManager<String, String> instrumented(final PuretxEngine engine) {
        final KafkaTransactionManager<String, String> manager = new KafkaTransactionManager<>(new FakeProducerFactory());
        new PuretxTransactionManagerPostProcessor(() -> engine, new InstrumentationReport())
                .postProcessAfterInitialization(manager, "kafkaTransactionManager");
        return manager;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Transaction-capable, with producers that accept begin/commit/abort and do nothing. */
    private static final class FakeProducerFactory implements ProducerFactory<String, String> {

        @Override
        public Producer<String, String> createProducer() {
            return fakeProducer();
        }

        @Override
        public Producer<String, String> createProducer(final String txIdPrefix) {
            return fakeProducer();
        }

        @Override
        public boolean transactionCapable() {
            return true;
        }

        @Override
        public String getTransactionIdPrefix() {
            return "tx-";
        }

        @SuppressWarnings("unchecked")
        private static Producer<String, String> fakeProducer() {
            return (Producer<String, String>) Proxy.newProxyInstance(
                    Producer.class.getClassLoader(),
                    new Class<?>[] {Producer.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "fake producer";
                        default -> null;
                    });
        }
    }
}
