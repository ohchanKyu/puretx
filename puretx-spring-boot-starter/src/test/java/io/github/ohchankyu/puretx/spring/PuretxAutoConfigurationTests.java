package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxMode;
import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationListener;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.http.PuretxClientHttpRequestInterceptor;
import io.github.ohchankyu.puretx.spring.http.PuretxExchangeFilterFunction;
import io.github.ohchankyu.puretx.spring.metrics.PuretxMetricsListener;
import io.github.ohchankyu.puretx.spring.tx.SpringTransactionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** What ends up in the context, and what does not. */
class PuretxAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PuretxAutoConfiguration.class));

    @Test
    @DisplayName("the defaults give you a working engine in WARN mode")
    void registersEngineByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PuretxEngine.class);
            assertThat(context).hasSingleBean(SpringTransactionProbe.class);
            assertThat(context).hasSingleBean(PuretxClientHttpRequestInterceptor.class);
            assertThat(context).hasSingleBean(PuretxExchangeFilterFunction.class);
            assertThat(context.getBean(PuretxEngine.class).settings().mode()).isEqualTo(PuretxMode.WARN);
        });
    }

    @Test
    @DisplayName("puretx.enabled=false leaves nothing behind")
    void backsOffEntirelyWhenDisabled() {
        runner.withPropertyValues("puretx.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PuretxEngine.class));
    }

    @Test
    @DisplayName("a detector that is switched off is not wired at all")
    void skipsDisabledDetectors() {
        runner.withPropertyValues("puretx.detectors.http=false").run(context -> {
            assertThat(context).doesNotHaveBean(PuretxClientHttpRequestInterceptor.class);
            assertThat(context).doesNotHaveBean(PuretxExchangeFilterFunction.class);
            assertThat(context.getBean(PuretxEngine.class).settings().detects(ViolationType.HTTP_CALL))
                    .isFalse();
        });
    }

    @Test
    @DisplayName("properties reach the engine")
    void bindsProperties() {
        runner.withPropertyValues(
                        "puretx.mode=FAIL",
                        "puretx.max-duration=750ms",
                        "puretx.ignore=com.acme.legacy,com.acme.**.Generated",
                        "puretx.include-call-path=false",
                        "puretx.record-limit=5")
                .run(context -> {
                    var settings = context.getBean(PuretxEngine.class).settings();
                    assertThat(settings.mode()).isEqualTo(PuretxMode.FAIL);
                    assertThat(settings.maxDuration()).isEqualTo(Duration.ofMillis(750));
                    assertThat(settings.ignore()).containsExactly("com.acme.legacy", "com.acme.**.Generated");
                    assertThat(settings.includeCallPath()).isFalse();
                    assertThat(settings.recordLimit()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("your own listener receives violations alongside the log")
    void registersUserListeners() {
        runner.withUserConfiguration(RecordingListenerConfiguration.class).run(context -> {
            PuretxEngine engine = context.getBean(PuretxEngine.class);
            engine.setProbe(() -> new TransactionInfo("com.acme.OrderService.createOrder", 10, false, false, ""));

            engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

            assertThat(context.getBean(RecordingListener.class).violations).hasSize(1);
        });
    }

    @Test
    @DisplayName("a probe of your own replaces the Spring one")
    void backsOffFromUserSuppliedProbe() {
        runner.withUserConfiguration(CustomProbeConfiguration.class).run(context -> {
            assertThat(context).doesNotHaveBean(SpringTransactionProbe.class);
            assertThat(context).hasSingleBean(TransactionProbe.class);
        });
    }

    @Test
    @DisplayName("the metrics listener is wired whenever micrometer is on the classpath")
    void registersTheMetricsListener() {
        runner.run(context -> assertThat(context).hasSingleBean(PuretxMetricsListener.class));
    }

    @Test
    @DisplayName("puretx.metrics.enabled=false leaves the registry alone")
    void skipsMetricsWhenSwitchedOff() {
        runner.withPropertyValues("puretx.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PuretxMetricsListener.class));
    }

    @Test
    @DisplayName("a violation reaches the registry through the listener the engine picked up")
    void publishesViolationsToTheRegistry() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            final PuretxEngine engine = context.getBean(PuretxEngine.class);
            engine.setProbe(() -> new TransactionInfo("com.acme.OrderService.createOrder", 10, false, false, ""));

            engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

            assertThat(context.getBean(MeterRegistry.class)
                    .counter("puretx.violations", "type", "http").count()).isEqualTo(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RecordingListenerConfiguration {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener implements ViolationListener {

        final List<Violation> violations = new ArrayList<>();

        @Override
        public void onViolation(final Violation violation) {
            violations.add(violation);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomProbeConfiguration {

        @Bean
        TransactionProbe transactionProbe() {
            return TransactionProbe.NONE;
        }
    }
}
