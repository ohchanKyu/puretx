package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.RetryingPaymentClient;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * puretx has to be the outermost interceptor, or the number it reports is not the number that matters.
 *
 * <p>A client that retries runs the rest of the chain once per attempt. Sitting inside that loop,
 * puretx would time a single attempt while the transaction is held for the whole sequence — and
 * because an interceptor chain is single-use, the retries would skip it entirely and report a
 * duration of zero.
 */
@PuretxIntegrationTest
class InterceptorOrderTests {

    private static final long WHOLE_SEQUENCE_MILLIS =
            RetryingPaymentClient.BACKOFF_MILLIS * (RetryingPaymentClient.ATTEMPTS - 1);

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void reset() {
        engine.store().clear();
        server.setStatus(503);
    }

    @AfterEach
    void restore() {
        server.setStatus(200);
    }

    @Test
    @DisplayName("a retried call is timed over the whole sequence, not one attempt of it")
    void timesTheWholeRetrySequence() {
        orderService.createOrderWithRetryingClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.durationMillis())
                        .as("the transaction was held for every attempt, so that is what to report")
                        .isGreaterThanOrEqualTo(WHOLE_SEQUENCE_MILLIS));
    }

    @Test
    @DisplayName("the call site is the application's, not the retry interceptor that wrapped it")
    void reportsTheApplicationCallSiteRatherThanTheInterceptor() {
        orderService.createOrderWithRetryingClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.origin()).isNotNull();
            assertThat(violation.origin().getClassName())
                    .doesNotContain("RetryInterceptor");
        });
    }
}
