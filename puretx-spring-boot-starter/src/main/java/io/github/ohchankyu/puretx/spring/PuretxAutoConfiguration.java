package io.github.ohchankyu.puretx.spring;

import feign.RequestInterceptor;
import io.github.ohchankyu.puretx.LoggingViolationListener;
import io.github.ohchankyu.puretx.Puretx;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.ViolationListener;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
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
 * producer gets no Kafka detector, one without {@code spring-web} gets no HTTP detector. Nothing
 * in here replaces or proxies an application bean — the transaction managers get a listener,
 * the HTTP clients get an interceptor, and both keep their own type.
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

    @Bean
    @ConditionalOnMissingBean
    public PuretxEngine puretxEngine(final PuretxProperties properties, final TransactionProbe probe,
            final ObjectProvider<ViolationListener> listeners) {
        PuretxEngine engine = new PuretxEngine(properties.toSettings(), probe);
        if (properties.isLog()) {
            engine.addListener(new LoggingViolationListener());
        }
        // After the logger on purpose. A call recorded once its transaction has already ended emits
        // the summary from here, and the summary should follow the violations it explains.
        engine.addListener(TransactionScopeManager.callRecorder(() -> engine));
        listeners.orderedStream().forEach(engine::addListener);
        Puretx.setEngine(engine);

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
        RestTemplateCustomizer puretxRestTemplateCustomizer(final PuretxClientHttpRequestInterceptor interceptor) {
            return interceptor::installOn;
        }

        /** Covers {@code new RestTemplate()} beans, which never see the builder's customizers. */
        @Bean
        static PuretxRestTemplatePostProcessor puretxRestTemplatePostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxRestTemplatePostProcessor(lazy(engine), report.getObject());
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
        WebClientCustomizer puretxWebClientCustomizer(final PuretxExchangeFilterFunction filter,
                final InstrumentationReport report) {
            return builder -> builder.filters(filters -> {
                if (filters.stream().noneMatch(PuretxExchangeFilterFunction.class::isInstance)) {
                    filters.add(0, filter);
                    report.instrumented("WebClient.Builder");
                }
            });
        }

        /** Covers clients built by the static factory, which no customizer ever sees. */
        @Bean
        static PuretxWebClientPostProcessor puretxWebClientPostProcessor(
                final ObjectProvider<PuretxEngine> engine, final ObjectProvider<InstrumentationReport> report) {
            return new PuretxWebClientPostProcessor(lazy(engine), report.getObject());
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
}
