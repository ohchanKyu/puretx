package io.github.ohchankyu.puretx.spring.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import io.github.ohchankyu.puretx.ImpureTransactionException;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxMode;
import io.github.ohchankyu.puretx.PuretxSettings;
import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.UncategorizedAmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitResourceHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The same violation as a Kafka send, over AMQP: a rollback takes the row back and the message
 * is already gone.
 */
class RabbitDetectionTests {

    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

    private Channel channel;

    @BeforeEach
    void connect() throws IOException {
        final Connection connection = mock(Connection.class);
        channel = mock(Channel.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(anyBoolean())).thenReturn(channel);
        when(channel.isOpen()).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.clear();
        if (TransactionSynchronizationManager.hasResource(connectionFactory)) {
            TransactionSynchronizationManager.unbindResource(connectionFactory);
        }
    }

    @Test
    @DisplayName("a publish inside a transaction is reported, and still goes out in WARN mode")
    void reportsPublishInsideTransaction() throws IOException {
        final PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        final RabbitTemplate template = instrumented(engine);

        template.convertAndSend("orders", "order.created", "payload");

        verify(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.MESSAGE_PUBLISH);
            assertThat(violation.summary()).isEqualTo("AMQP publish -> exchange 'orders' routing key 'order.created'");
        });
    }

    @Test
    @DisplayName("a publish with no transaction open is not reported")
    void ignoresPublishOutsideTransaction() {
        final PuretxEngine engine = engine(PuretxMode.WARN, TransactionProbe.NONE);
        final RabbitTemplate template = instrumented(engine);

        template.convertAndSend("orders", "order.created", "payload");

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a publish on a transacted channel synchronised with the transaction is the pattern working as designed")
    void ignoresPublishOnSynchronisedTransactedChannel() {
        final PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        final RabbitTemplate template = instrumented(engine);
        template.setChannelTransacted(true);
        TransactionSynchronizationManager.bindResource(connectionFactory, new RabbitResourceHolder());
        TransactionSynchronizationManager.initSynchronization();

        template.convertAndSend("orders", "order.created", "payload");

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("FAIL mode fails the call before the message leaves; RabbitTemplate wraps the exception")
    void failsBeforePublishingInFailMode() throws IOException {
        final PuretxEngine engine = engine(PuretxMode.FAIL, transactionActive());
        final RabbitTemplate template = instrumented(engine);

        assertThatThrownBy(() -> template.convertAndSend("orders", "order.created", "payload"))
                .isInstanceOf(UncategorizedAmqpException.class)
                .cause().isInstanceOf(ImpureTransactionException.class)
                .hasMessageContaining("AMQP publish -> exchange 'orders' routing key 'order.created'");

        verify(channel, never()).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("the post-processor attaches itself to a template exactly once")
    void attachesItselfOnce() {
        final PuretxEngine engine = engine(PuretxMode.WARN, transactionActive());
        final RabbitTemplate template = new RabbitTemplate(connectionFactory);
        final PuretxRabbitTemplatePostProcessor postProcessor =
                new PuretxRabbitTemplatePostProcessor(() -> engine, new InstrumentationReport());

        postProcessor.postProcessAfterInitialization(template, "rabbitTemplate");
        postProcessor.postProcessAfterInitialization(template, "rabbitTemplate");
        template.convertAndSend("orders", "order.created", "payload");

        assertThat(engine.store().total()).isEqualTo(1);
    }

    private RabbitTemplate instrumented(final PuretxEngine engine) {
        final RabbitTemplate template = new RabbitTemplate(connectionFactory);
        new PuretxRabbitTemplatePostProcessor(() -> engine, new InstrumentationReport()).install(template);
        return template;
    }

    private static PuretxEngine engine(final PuretxMode mode, final TransactionProbe probe) {
        return new PuretxEngine(PuretxSettings.builder().mode(mode).build(), probe);
    }

    private static TransactionProbe transactionActive() {
        return () -> new TransactionInfo("com.acme.orders.OrderService.createOrder", 42, false, false, "");
    }
}
