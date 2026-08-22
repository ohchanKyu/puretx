package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** WebClient blocked on inside a transaction is the same violation with a reactive API on top. */
@PuretxIntegrationTest
class WebClientDetectionTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void reset() {
        engine.store().clear();
    }

    @Test
    @DisplayName("a blocking WebClient call inside a transaction is reported")
    void reportsBlockingWebClientCallsInsideTransaction() {
        orderService.createOrderWithWebClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.summary()).startsWith("HTTP GET").contains("/charge");
            assertThat(violation.transaction().displayName()).isEqualTo("OrderService.createOrderWithWebClient");
        });
    }

    @Test
    @DisplayName("a WebClient call outside a transaction is not")
    void ignoresWebClientCallsOutsideTransaction() {
        orderService.createOrderWithWebClientOutsideTransaction(server.url());

        assertThat(engine.store().all()).isEmpty();
    }
}
