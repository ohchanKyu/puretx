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
import io.github.ohchankyu.puretx.spring.kafka.PuretxProducerFactoryPostProcessor;
import io.github.ohchankyu.puretx.spring.tx.PuretxTransactionManagerPostProcessor;
import io.github.ohchankyu.puretx.spring.tx.SpringTransactionProbe;
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
        listeners.orderedStream().forEach(engine::addListener);
        // Makes Puretx.suppress(...) and Puretx.violations() work without injecting anything.
        Puretx.setEngine(engine);

        // Applications never reference puretx from their own code, so a missing dependency or an
        // unmet condition would leave everything working and puretx silently absent. Saying so once
        // is the only way to tell "nothing to report" apart from "never switched on".
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
        RestTemplateCustomizer puretxRestTemplateCustomizer(final PuretxClientHttpRequestInterceptor interceptor,
                final InstrumentationReport report) {
            report.watchingHttp();
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
            report.watchingHttp();
            return builder -> builder.filters(filters -> {
                if (filters.stream().noneMatch(PuretxExchangeFilterFunction.class::isInstance)) {
                    filters.add(0, filter);
                    report.instrumented("WebClient.Builder");
                }
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnProperty(prefix = "puretx.detectors", name = "http", havingValue = "true", matchIfMissing = true)
    static class FeignDetection {

        @Bean
        PuretxFeignRequestInterceptor puretxFeignRequestInterceptor(final PuretxEngine engine) {
            return new PuretxFeignRequestInterceptor(engine);
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
