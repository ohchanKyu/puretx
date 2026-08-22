package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.PuretxSettings;
import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionProbe;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.http.PuretxFeignRequestInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Feign is the one client with nothing to post-process: registering the interceptor bean is the
 * instrumentation, and there is no way to confirm afterwards that anything consumed it.
 *
 * <p>So the detector only switches on when spring-cloud-openfeign is present, which is what makes
 * a {@code RequestInterceptor} bean reach a client at all. feign-core arrives as a transitive
 * dependency of plenty of things; activating on that alone would count as instrumentation while
 * attaching to nothing, and would suppress the warning that says no HTTP client was reached.
 */
class FeignDetectionTests {

    private static final TransactionInfo ACTIVE =
            new TransactionInfo("com.acme.orders.OrderService.createOrder", 12, false, false, "");

    @Test
    @DisplayName("a Feign call inside a transaction is reported, without a duration Feign cannot give")
    void reportsFeignCallsInsideATransaction() {
        final PuretxEngine engine = engine(() -> ACTIVE);
        final InstrumentationReport report = new InstrumentationReport();

        new PuretxFeignRequestInterceptor(engine, report).apply(chargeRequest());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.summary()).isEqualTo("HTTP POST https://pay.example.com/charge");
            assertThat(violation.hasDuration()).isFalse();
        });
    }

    @Test
    @DisplayName("a Feign call with no transaction open is not reported")
    void ignoresFeignCallsOutsideATransaction() {
        final PuretxEngine engine = engine(TransactionProbe.NONE);
        final InstrumentationReport report = new InstrumentationReport();

        new PuretxFeignRequestInterceptor(engine, report).apply(chargeRequest());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("feign-core on its own does not switch the detector on")
    void staysOffWithoutSpringCloudOpenFeign() {
        // feign-core is on this test classpath; spring-cloud-openfeign is not.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuretxAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(PuretxFeignRequestInterceptor.class));
    }

    private static RequestTemplate chargeRequest() {
        final RequestTemplate template = new RequestTemplate().method(Request.HttpMethod.POST).uri("/charge");
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, "https://pay.example.com"));
        return template;
    }

    private static PuretxEngine engine(final TransactionProbe probe) {
        return new PuretxEngine(PuretxSettings.defaults(), probe);
    }
}
