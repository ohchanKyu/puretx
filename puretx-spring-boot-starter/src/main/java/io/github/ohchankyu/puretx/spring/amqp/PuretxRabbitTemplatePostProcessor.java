package io.github.ohchankyu.puretx.spring.amqp;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.InstrumentationReport;
import java.util.function.Supplier;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.SingletonSupplier;

/**
 * Registers puretx's publish hook on every {@code RabbitTemplate} in the context.
 *
 * <p>Uses the template's own before-publish post-processor extension point, so the bean keeps
 * its type and everything built on top of it — {@code RabbitMessagingTemplate}, a hand-rolled
 * publisher — is covered without being proxied. The hook runs on the calling thread right before
 * {@code basicPublish}, which is the moment the answer is unambiguous.
 *
 * <p>A publish on a transacted channel that Spring has synchronised with the surrounding
 * transaction is not reported: the channel commits after the outer commit, so a rollback does
 * take the message back. That is the pattern working as designed, the same as a Kafka
 * transactional producer.
 *
 * <p>Two things the hook cannot offer. It runs before the publish, so the violation carries no
 * duration; and in {@code FAIL} mode {@code RabbitTemplate} wraps whatever it throws in an
 * {@code UncategorizedAmqpException}, so the {@code ImpureTransactionException} arrives as the
 * cause rather than the exception itself. The call still fails at the call site before the
 * message leaves, which is what {@code FAIL} is for.
 */
public final class PuretxRabbitTemplatePostProcessor implements BeanPostProcessor {

    private final Supplier<PuretxEngine> engineSupplier;

    private final InstrumentationReport report;

    public PuretxRabbitTemplatePostProcessor(final Supplier<PuretxEngine> engineSupplier, final InstrumentationReport report) {
        this.engineSupplier = SingletonSupplier.of(engineSupplier);
        this.report = report;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof RabbitTemplate template) {
            install(template);
        }
        return bean;
    }

    /**
     * Adds the hook to {@code template} unless one is already there.
     *
     * <p>{@code RabbitTemplate} has no getter for its before-publish post-processors, so
     * "already there" is checked by asking it to remove ours: a successful removal means it
     * was, and it is put straight back.
     */
    public void install(final RabbitTemplate template) {
        final PuretxPublishPostProcessor hook = new PuretxPublishPostProcessor(engineSupplier, template);
        if (template.removeBeforePublishPostProcessor(hook)) {
            template.addBeforePublishPostProcessors(hook);
            return;
        }
        template.addBeforePublishPostProcessors(hook);
        report.instrumented("RabbitTemplate");
    }

    /**
     * Placed first among the template's post-processors so that the check happens before any
     * compression or header rewriting the application configured, and equal to any other
     * instance for the same template so that {@link #install} can recognise its own.
     */
    static final class PuretxPublishPostProcessor implements MessagePostProcessor, Ordered {

        private final Supplier<PuretxEngine> engineSupplier;

        private final RabbitTemplate template;

        PuretxPublishPostProcessor(final Supplier<PuretxEngine> engineSupplier, final RabbitTemplate template) {
            this.engineSupplier = engineSupplier;
            this.template = template;
        }

        @Override
        public Message postProcessMessage(final Message message) {
            return postProcessMessage(message, null, "", "");
        }

        @Override
        public Message postProcessMessage(final Message message, final Correlation correlation,
                final String exchange, final String routingKey) {
            final PuretxEngine engine = engineSupplier.get();
            if (engine.isWatching(ViolationType.MESSAGE_PUBLISH) && !isSynchronisedWithTransaction()) {
                engine.report(ViolationType.MESSAGE_PUBLISH, () -> summarize(exchange, routingKey));
            }
            return message;
        }

        /** True when the template's transacted channel is bound to the current transaction. */
        private boolean isSynchronisedWithTransaction() {
            return template.isChannelTransacted()
                    && TransactionSynchronizationManager.getResource(template.getConnectionFactory()) != null;
        }

        private static String summarize(final String exchange, final String routingKey) {
            return "AMQP publish -> exchange '" + exchange + "' routing key '" + routingKey + "'";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof PuretxPublishPostProcessor that && that.template == template;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(template);
        }
    }
}
