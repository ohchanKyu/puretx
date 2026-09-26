package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The status arriving is not the end of the call. A non-buffering request factory streams the
 * body after it, and for a large response that download is most of the time the connection was
 * held. Each client here talks to a server that sends headers and a first chunk at once and the
 * rest of the body 400ms later.
 */
@PuretxIntegrationTest
class ResponseBodyTimingTests {

    private static final long BODY_DELAY_MILLIS = 400;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void slowBody() {
        engine.store().clear();
        server.setBodyDelayMillis(BODY_DELAY_MILLIS);
    }

    @AfterEach
    void restore() {
        server.setBodyDelayMillis(0);
    }

    @Test
    @DisplayName("RestTemplate with the default factory is timed to the end of the body")
    void restTemplateIncludesTheBody() {
        orderService.createOrder(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(BODY_DELAY_MILLIS));
    }

    @Test
    @DisplayName("RestClient from the static builder is timed to the end of the body")
    void restClientIncludesTheBody() {
        orderService.createOrderWithRestClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(BODY_DELAY_MILLIS));
    }

    @Test
    @DisplayName("WebClient is timed to the end of the body it was asked for")
    void webClientIncludesTheBody() {
        orderService.createOrderWithWebClient(server.url());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(engine.store().all()).singleElement().satisfies(violation ->
                        assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(BODY_DELAY_MILLIS)));
    }
}
