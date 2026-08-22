package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import io.github.ohchankyu.puretx.spring.http.PuretxFeignRequestInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** One test per HTTP client, so "supported" means the same thing for all four. */
@PuretxIntegrationTest
class HttpClientCoverageTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @Autowired
    private PuretxFeignRequestInterceptor feignInterceptor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void reset() {
        engine.store().clear();
    }

    @Test
    @DisplayName("RestClient calls inside a transaction are reported")
    void reportsRestClientCalls() {
        orderService.createOrderWithRestClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.transaction().displayName()).isEqualTo("OrderService.createOrderWithRestClient");
        });
    }

    @Test
    @DisplayName("Feign calls inside a transaction are reported, without a duration Feign cannot give us")
    void reportsFeignCalls() {
        RequestTemplate template = new RequestTemplate().method(Request.HttpMethod.POST).uri("/charge");
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, "https://pay.example.com"));

        transactionTemplate.executeWithoutResult(status -> feignInterceptor.apply(template));

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.summary()).isEqualTo("HTTP POST https://pay.example.com/charge");
            assertThat(violation.hasDuration()).isFalse();
        });
    }

    @Test
    @DisplayName("a Feign call with no transaction open is not reported")
    void ignoresFeignCallsOutsideTransaction() {
        RequestTemplate template = new RequestTemplate().method(Request.HttpMethod.POST).uri("/charge");

        feignInterceptor.apply(template);

        assertThat(engine.store().all()).isEmpty();
    }
}
