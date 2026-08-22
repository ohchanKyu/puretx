package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * WebClient blocked on inside a transaction is the same violation with a reactive API on top.
 *
 * <p>These wait rather than assert outright. The violation is recorded when the exchange
 * terminates, on whichever thread the client completes on, so a blocking caller can return just
 * before the report lands — an assertion made the instant {@code block()} returns is a coin toss
 * that happens to keep coming up heads on a fast loopback.
 */
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

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(engine.store().all()).singleElement().satisfies(violation -> {
                    assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
                    assertThat(violation.summary()).startsWith("HTTP GET").contains("/charge");
                    assertThat(violation.transaction().displayName())
                            .isEqualTo("OrderService.createOrderWithWebClient");
                }));
    }

    @Test
    @DisplayName("the filter is added exactly once, so a call is not reported twice")
    void reportsEachCallOnce() {
        orderService.createOrderWithWebClient(server.url());

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(engine.store().total()).isEqualTo(1));
    }

    @Test
    @DisplayName("a WebClient call outside a transaction is not")
    void ignoresWebClientCallsOutsideTransaction() {
        orderService.createOrderWithWebClientOutsideTransaction(server.url());

        // Stays empty rather than merely starts empty: a late report would still be a failure.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(engine.store().all()).isEmpty());
    }
}
