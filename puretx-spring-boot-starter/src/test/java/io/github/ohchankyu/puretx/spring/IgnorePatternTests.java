package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxTestApplication;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The escape hatch for code nobody is going to fix this quarter.
 *
 * <p>Without one, the first team to adopt puretx on an existing codebase gets a thousand warnings
 * and turns it off. With one, they can start from where they are and stop the number growing.
 */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "puretx.ignore=com.acme.orders.PaymentClient")
class IgnorePatternTests {

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
    @DisplayName("a call site covered by an ignore pattern is not reported")
    void ignoresMatchingCallSites() {
        orderService.createOrder(server.url());

        assertThat(engine.store().all()).isEmpty();
    }
}
