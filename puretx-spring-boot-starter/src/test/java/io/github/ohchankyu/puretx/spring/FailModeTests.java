package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxTestApplication;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.ImpureTransactionException;
import io.github.ohchankyu.puretx.PuretxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * FAIL mode: the configuration a build uses to stop new violations getting merged.
 *
 * <p>The exception is raised before the call goes out, so a test does not have to wait for — or
 * reach — the remote system it should not have been calling in the first place.
 */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "puretx.mode=FAIL")
class FailModeTests {

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
    @DisplayName("an impure call throws, and the message says what and where")
    void throwsOnImpureCall() {
        assertThatThrownBy(() -> orderService.createOrder(server.url()))
                .isInstanceOf(ImpureTransactionException.class)
                .hasMessageContaining("IMPURE TRANSACTION detected")
                .hasMessageContaining("OrderService.createOrder")
                .hasMessageContaining("HTTP GET");

        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("code that was already correct is unaffected by FAIL mode")
    void leavesCorrectCodeAlone() {
        orderService.createOrderChargingAfterCommit(server.url());
        orderService.createOrderWithoutTransaction(server.url());
        orderService.createOrderPublishingEvent(server.url());

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("a suppressed call is still exempt in FAIL mode")
    void respectsSuppression() {
        orderService.createOrderWithAcknowledgedCall(server.url());

        assertThat(engine.store().all()).isEmpty();
    }
}
