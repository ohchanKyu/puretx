package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxTestApplication;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code com.acme.*} is the natural way to write "my packages", and read as a pattern it would
 * cover {@code com.acme.X} only, leaving every real frame unrecognised: no call site, and a
 * path of framework frames. Worse output than not setting it at all.
 */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "puretx.app-packages=com.acme.*")
class AppPackageWildcardTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @Test
    @DisplayName("a trailing star still finds the call site in a deeper package")
    void trailingStarCoversDeeperPackages() {
        engine.store().clear();

        orderService.createOrder(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.origin()).isNotNull();
            assertThat(violation.origin().getClassName()).startsWith("com.acme.orders.");
            assertThat(violation.callPath()).allSatisfy(frame ->
                    assertThat(frame.getClassName()).startsWith("com.acme.orders."));
        });
    }
}
