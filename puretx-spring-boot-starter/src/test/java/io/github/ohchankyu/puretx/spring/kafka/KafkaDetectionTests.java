package io.github.ohchankyu.puretx.spring.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.ohchankyu.puretx.ImpureTransactionException;
import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxMode;
import io.github.ohchankyu.puretx.PuretxSettings;
import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishing inside a database transaction is the violation that cannot be undone: a rollback
 * takes the row back, but the message is already on its way to whoever was listening.
 */
class KafkaDetectionTests {

    private final Object producerFactoryKey = new Object();
    private final List<ProducerRecord<?, ?>> sent = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clear();
        if (TransactionSynchronizationManager.hasResource(producerFactoryKey)) {
            TransactionSynchronizationManager.unbindResource(producerFactoryKey);
        }
    }

    @Test
    @DisplayName("a send inside a transaction is reported, and still goes through in WARN mode")
    void reportsSendInsideTransaction() {
        PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        Producer<String, String> producer = PuretxProducerProxy.wrap(fakeProducer(), engine, producerFactoryKey);

        producer.send(new ProducerRecord<>("orders", "key", "payload"));

        assertThat(sent).hasSize(1);
        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.MESSAGE_PUBLISH);
            assertThat(violation.summary()).isEqualTo("Kafka send -> topic 'orders'");
        });
    }

    @Test
    @DisplayName("a send is timed, so a publish does not summarise as costing nothing")
    void timesTheSend() {
        final PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        final Producer<String, String> producer =
                PuretxProducerProxy.wrap(slowProducer(30), engine, producerFactoryKey);

        producer.send(new ProducerRecord<>("orders", "key", "payload"));

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(30));
    }

    @Test
    @DisplayName("a send with no transaction open is not reported")
    void ignoresSendOutsideTransaction() {
        PuretxEngine engine = engine(PuretxMode.WARN, TransactionProbe.NONE);
        Producer<String, String> producer = PuretxProducerProxy.wrap(fakeProducer(), engine, producerFactoryKey);

        producer.send(new ProducerRecord<>("orders", "key", "payload"));

        assertThat(sent).hasSize(1);
        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a send inside a Kafka-managed transaction is the transactional producer working as intended")
    void ignoresSendInsideKafkaTransaction() {
        PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        Producer<String, String> producer = PuretxProducerProxy.wrap(fakeProducer(), engine, producerFactoryKey);
        TransactionSynchronizationManager.bindResource(producerFactoryKey, new Object());

        producer.send(new ProducerRecord<>("orders", "key", "payload"));

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("FAIL mode throws before the record leaves — Kafka's own interceptors could not")
    void throwsBeforeSendingInFailMode() {
        PuretxEngine engine = engine(PuretxMode.FAIL, transactionActive());
        Producer<String, String> producer = PuretxProducerProxy.wrap(fakeProducer(), engine, producerFactoryKey);

        assertThatThrownBy(() -> producer.send(new ProducerRecord<>("orders", "key", "payload")))
                .isInstanceOf(ImpureTransactionException.class)
                .hasMessageContaining("Kafka send -> topic 'orders'");

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("the post-processor attaches itself to a producer factory exactly once")
    void registersItselfOnProducerFactoriesOnce() {
        PuretxEngine engine = engine(PuretxMode.WARN, TransactionProbe.NONE);
        DefaultKafkaProducerFactory<String, String> factory =
                new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", "localhost:9092"));
        PuretxProducerFactoryPostProcessor postProcessor =
                new PuretxProducerFactoryPostProcessor(() -> engine, new InstrumentationReport());

        postProcessor.postProcessAfterInitialization(factory, "kafkaProducerFactory");
        postProcessor.postProcessAfterInitialization(factory, "kafkaProducerFactory");

        assertThat(factory.getPostProcessors()).hasSize(1);
    }

    @Test
    @DisplayName("a factory that ignores post-processors is reported as unwatched, not as instrumented")
    void doesNotClaimToInstrumentAFactoryThatIgnoresThem() {
        final List<ILoggingEvent> events = captureLog();
        final PuretxEngine engine = engine(PuretxMode.WARN, TransactionProbe.NONE);
        final InstrumentationReport report = new InstrumentationReport();
        // Overrides nothing: ProducerFactory's default addPostProcessor has an empty body and
        // getPostProcessors returns an empty list, so the registration silently evaporates.
        final ProducerFactory<String, String> unsupported = new ProducerFactory<>() {
            @Override
            public Producer<String, String> createProducer() {
                return null;
            }
        };

        new PuretxProducerFactoryPostProcessor(() -> engine, report)
                .postProcessAfterInitialization(unsupported, "customProducerFactory");
        report.instrumented("transaction manager");
        report.afterSingletonsInstantiated();

        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("does not support producer post-processors"))
                .noneSatisfy(message -> assertThat(message).contains("Kafka producer factory"));
    }

    private static List<ILoggingEvent> captureLog() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        final ch.qos.logback.classic.Logger logger = context.getLogger(Puretx.LOGGER_NAME);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender.list;
    }

    private static PuretxEngine engine(final PuretxMode mode, final TransactionProbe probe) {
        return new PuretxEngine(PuretxSettings.builder().mode(mode).build(), probe);
    }

    private static TransactionProbe transactionActive() {
        return () -> new TransactionInfo("com.acme.orders.OrderService.createOrder", 42, false, false, "");
    }

    /** Stands in for the metadata wait that makes a real send block for up to max.block.ms. */
    @SuppressWarnings("unchecked")
    private Producer<String, String> slowProducer(final long millis) {
        return (Producer<String, String>) Proxy.newProxyInstance(
                Producer.class.getClassLoader(),
                new Class<?>[] {Producer.class},
                (proxy, method, args) -> {
                    if ("send".equals(method.getName())) {
                        Thread.sleep(millis);
                        sent.add((ProducerRecord<?, ?>) args[0]);
                        return CompletableFuture.completedFuture(null);
                    }
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private Producer<String, String> fakeProducer() {
        return (Producer<String, String>) Proxy.newProxyInstance(
                Producer.class.getClassLoader(),
                new Class<?>[] {Producer.class},
                (proxy, method, args) -> {
                    if ("send".equals(method.getName())) {
                        sent.add((ProducerRecord<?, ?>) args[0]);
                        return CompletableFuture.completedFuture(null);
                    }
                    return null;
                });
    }
}
