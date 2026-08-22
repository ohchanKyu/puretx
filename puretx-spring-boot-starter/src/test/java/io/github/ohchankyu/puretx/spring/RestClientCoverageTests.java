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

/**
 * A RestClient built by the static factory still gets instrumented.
 *
 * <p>This is the case a {@code RestClientCustomizer} cannot reach, and it is the way most
 * applications build one — six out of six in the codebase that first reported the gap. Until the
 * bean post-processor existed, adding puretx to such an application detected nothing at all.
 */
@PuretxIntegrationTest
class RestClientCoverageTests {

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
    @DisplayName("a RestClient from the static builder is instrumented, not just one from the builder bean")
    void instrumentsRestClientsBuiltByTheStaticFactory() {
        orderService.createOrderWithRestClient(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.transaction().displayName())
                    .isEqualTo("OrderService.createOrderWithRestClient");
        });
    }

    @Test
    @DisplayName("the interceptor is added exactly once, so a call is not reported twice")
    void reportsEachCallOnce() {
        orderService.createOrderWithRestClient(server.url());

        assertThat(engine.store().total()).isEqualTo(1);
    }
}
