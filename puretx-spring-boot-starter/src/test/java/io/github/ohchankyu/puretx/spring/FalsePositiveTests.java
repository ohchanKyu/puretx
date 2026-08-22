package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cases puretx must stay quiet about.
 *
 * <p>These come first, and they matter more than the detection tests. A warning that fires on
 * correct code is worse than no warning at all: the first thing anyone does with a noisy library
 * is delete it, and then the real violations go unreported too.
 */
@PuretxIntegrationTest
class FalsePositiveTests {

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
    @DisplayName("an HTTP call with no transaction around it is not a violation")
    void ignoresCallsOutsideAnyTransaction() {
        orderService.createOrderWithoutTransaction(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a call deferred to afterCommit is the recommended fix, not a violation")
    void ignoresCallsDeferredToAfterCommit() {
        orderService.createOrderChargingAfterCommit(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("@TransactionalEventListener(AFTER_COMMIT) handlers run after the commit")
    void ignoresAfterCommitEventListeners() {
        orderService.createOrderPublishingEvent(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a transaction opened by the test framework is not the application's transaction")
    @Transactional
    void ignoresSpringTestManagedTransactions() {
        orderService.createOrderWithoutTransaction(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("Puretx.suppress marks a call the team has already decided about")
    void ignoresExplicitlySuppressedCalls() {
        orderService.createOrderWithAcknowledgedCall(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a transaction that finishes inside the threshold is not reported")
    void ignoresShortTransactions() {
        orderService.slowOrder(5);

        assertThat(engine.store().all()).isEmpty();
    }
}
