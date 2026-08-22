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
import org.springframework.transaction.annotation.Transactional;

/** The opt-in half of the test-transaction story; the default is covered in {@code FalsePositiveTests}. */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "puretx.detect-in-test-transactions=true")
class TestManagedTransactionTests {

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
    @DisplayName("with the flag on, a test-managed transaction is reported and labelled as one")
    @Transactional
    void reportsTestManagedTransactionsWhenAskedTo() {
        orderService.createOrderWithoutTransaction(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.transaction().testManaged()).isTrue());
    }
}
