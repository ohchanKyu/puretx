package io.github.ohchankyu.puretx.spring;

import feign.RequestInterceptor;
import io.github.ohchankyu.puretx.LoggingViolationListener;
import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.ViolationListener;
import io.github.ohchankyu.puretx.spring.amqp.PuretxRabbitTemplatePostProcessor;
import io.github.ohchankyu.puretx.spring.http.PuretxClientHttpRequestInterceptor;
import io.github.ohchankyu.puretx.spring.http.PuretxExchangeFilterFunction;
import io.github.ohchankyu.puretx.spring.http.PuretxFeignRequestInterceptor;
import io.github.ohchankyu.puretx.spring.http.PuretxRestClientPostProcessor;
import io.github.ohchankyu.puretx.spring.http.PuretxRestTemplatePostProcessor;
import io.github.ohchankyu.puretx.spring.http.PuretxWebClientPostProcessor;
import io.github.ohchankyu.puretx.spring.kafka.PuretxProducerFactoryPostProcessor;
import io.github.ohchankyu.puretx.spring.metrics.PuretxMetricsListener;
import io.github.ohchankyu.puretx.spring.tx.PuretxTransactionManagerPostProcessor;
import io.github.ohchankyu.puretx.spring.tx.SpringTransactionProbe;
import io.github.ohchankyu.puretx.spring.tx.TransactionScopeManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires puretx into a Spring Boot application.
 *
 * <p>Everything below the engine is optional and conditional: an application without a Kafka
 * producer gets no Kafka detector, one without {@code spring-web} gets no HTTP detector. The
 * transaction managers get a listener and keep their identity. HTTP clients get an interceptor;
 * {@code RestClient} and {@code WebClient} are immutable, so those two are rebuilt through
 * {@code mutate()} with the interceptor added and everything else carried over.
 *
 * <p>Each HTTP client is reached twice: a {@code BeanPostProcessor} for clients registered as
 * beans, however they were built, and a Boot customizer for clients built from an injected
 * builder inside a constructor, which never become beans. Spring Boot 4 moved the customizer
 * interfaces to new packages, so each one is declared twice under a class-name condition and
 * whichever exists on the classpath is used.
 */
@AutoConfiguration
@ConditionalOnClass(PlatformTransactionManager.class)
@ConditionalOnProperty(prefix = "puretx", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PuretxProperties.class)
public class PuretxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    @Bean
    @ConditionalOnMissingBean
    public TransactionProbe puretxTransactionProbe() {
        return new SpringTransactionProbe();
    }

    /**
     * Builds the engine and attaches the listeners.
     *
     * <p>The call recorder goes on after the logging listener on purpose: a call recorded once its
     * transaction has already ended emits the summary from there, and a summary should follow the
     * violations it explains.
     */
    @Bean
    @ConditionalOnMissingBean
    public PuretxEngine puretxEngine(final PuretxProperties properties, final TransactionProbe probe,
            final ObjectProvider<ViolationListener> listeners) {
        PuretxEngine engine = new PuretxEngine(properties.toSettings(), probe);
        if (properties.isLog()) {
            engine.addListener(new LoggingViolationListener());
        }
        engine.addListener(TransactionScopeManager.callRecorder(() -> engine));
        listeners.orderedStream().forEach(engine::addListener);
        Puretx.setEngine(engine);
        Puretx.setScopedEngine(TransactionScopeManager::currentEngine);

        log.info("[puretx] {}", engine.settings().describe());
        return engine;
    }

    @Bean
    public InstrumentationReport puretxInstrumentationReport() {
        return new InstrumentationReport();
    }

    @Bean
    public static PuretxTransactionManagerPostProcessor puretxTransactionManagerPostProcessor(
            final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
        return new PuretxTransactionManagerPostProcessor(lazy(engine), report.getObject());
    }

    /**
     * Bean post-processors are created before almost everything else, so they cannot ask for the
     * engine directly without dragging it — and the properties it binds — into existence too early.
     */
    private static Supplier<PuretxEngine> lazy(final ObjectProvider<PuretxEngine> engine) {
        return SingletonSupplier.of(engine::getObject);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "http", havingValue = "true", matchIfMissing = true)
    static class RestTemplateDetection {

        @Bean
        PuretxClientHttpRequestInterceptor puretxClientHttpRequestInterceptor(final PuretxEngine engine) {
            return new PuretxClientHttpRequestInterceptor(engine);
        }

        @Bean
        static PuretxRestTemplatePostProcessor puretxRestTemplatePostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxRestTemplatePostProcessor(lazy(engine), report.getObject());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.web.client.RestTemplateCustomizer")
        static class Boot3Customizer {

            @Bean
            org.springframework.boot.web.client.RestTemplateCustomizer puretxRestTemplateCustomizer(
                    final PuretxClientHttpRequestInterceptor interceptor, final InstrumentationReport report) {
                return template -> interceptor.installOn(template, report);
            }
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.restclient.RestTemplateCustomizer")
        static class Boot4Customizer {

            @Bean
            org.springframework.boot.restclient.RestTemplateCustomizer puretxRestTemplateCustomizer(
                    final PuretxClientHttpRequestInterceptor interceptor, final InstrumentationReport report) {
                return template -> interceptor.installOn(template, report);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "http", havingValue = "true", matchIfMissing = true)
    static class RestClientDetection {

        @Bean
        static PuretxRestClientPostProcessor puretxRestClientPostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxRestClientPostProcessor(lazy(engine), report.getObject());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.web.client.RestClientCustomizer")
        static class Boot3Customizer {

            @Bean
            org.springframework.boot.web.client.RestClientCustomizer puretxRestClientCustomizer(
                    final PuretxClientHttpRequestInterceptor interceptor, final InstrumentationReport report) {
                return builder -> interceptor.installOn(builder, report);
            }
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.restclient.RestClientCustomizer")
        static class Boot4Customizer {

            @Bean
            org.springframework.boot.restclient.RestClientCustomizer puretxRestClientCustomizer(
                    final PuretxClientHttpRequestInterceptor interceptor, final InstrumentationReport report) {
                return builder -> interceptor.installOn(builder, report);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebClient.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "http", havingValue = "true", matchIfMissing = true)
    static class WebClientDetection {

        @Bean
        PuretxExchangeFilterFunction puretxExchangeFilterFunction(final PuretxEngine engine) {
            return new PuretxExchangeFilterFunction(engine);
        }

        @Bean
        static PuretxWebClientPostProcessor puretxWebClientPostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxWebClientPostProcessor(lazy(engine), report.getObject());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.web.reactive.function.client.WebClientCustomizer")
        static class Boot3Customizer {

            @Bean
            org.springframework.boot.web.reactive.function.client.WebClientCustomizer puretxWebClientCustomizer(
                    final PuretxExchangeFilterFunction filter, final InstrumentationReport report) {
                return builder -> filter.installOn(builder, report);
            }
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.boot.webclient.WebClientCustomizer")
        static class Boot4Customizer {

            @Bean
            org.springframework.boot.webclient.WebClientCustomizer puretxWebClientCustomizer(
                    final PuretxExchangeFilterFunction filter, final InstrumentationReport report) {
                return builder -> filter.installOn(builder, report);
            }
        }
    }

    /**
     * A {@code RequestInterceptor} bean only reaches a Feign client through spring-cloud-openfeign.
     * feign-core alone often arrives as a transitive dependency of something else, and registering
     * the bean there would count as instrumentation while attaching to nothing — and, worse,
     * suppress the warning that says no HTTP client was reached.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(value = RequestInterceptor.class,
            name = "org.springframework.cloud.openfeign.FeignClientBuilder")
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "http", havingValue = "true", matchIfMissing = true)
    static class FeignDetection {

        @Bean
        PuretxFeignRequestInterceptor puretxFeignRequestInterceptor(final PuretxEngine engine, final InstrumentationReport report) {
            return new PuretxFeignRequestInterceptor(engine, report);
        }
    }

    /**
     * Registered as a {@link io.github.ohchankyu.puretx.ViolationListener}, so the engine picks it
     * up like any other. The registry is resolved lazily rather than required as a bean: that keeps
     * this independent of when the metrics auto-configuration happens to run, and lets the listener
     * do nothing at all if no registry ever turns up.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "puretx.metrics", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class MetricsPublishing {

        @Bean
        PuretxMetricsListener puretxMetricsListener(final ObjectProvider<MeterRegistry> registry) {
            return new PuretxMetricsListener(registry::getIfAvailable);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProducerFactory.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "messaging", havingValue = "true",
            matchIfMissing = true)
    static class KafkaDetection {

        @Bean
        static PuretxProducerFactoryPostProcessor puretxProducerFactoryPostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxProducerFactoryPostProcessor(lazy(engine), report.getObject());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "messaging", havingValue = "true",
            matchIfMissing = true)
    static class RabbitDetection {

        @Bean
        static PuretxRabbitTemplatePostProcessor puretxRabbitTemplatePostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxRabbitTemplatePostProcessor(lazy(engine), report.getObject());
        }
    }
}
