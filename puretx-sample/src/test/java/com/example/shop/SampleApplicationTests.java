package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shop.service.OrderService;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The sample doubles as an end-to-end check: same application, both versions of the same method. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SampleApplicationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void reset() {
        engine.store().clear();
    }

    @Test
    @DisplayName("charging inside the transaction is reported, twice over")
    void reportsTheImpureVersion() {
        orderService.placeOrder("a book");

        assertThat(engine.store().all()).extracting(Violation::type).containsExactly(
                // The remote call itself...
                ViolationType.HTTP_CALL,
                // ...and the transaction it kept open while waiting for it.
                ViolationType.LONG_TRANSACTION);
    }

    @Test
    @DisplayName("charging after the commit is reported not at all")
    void staysQuietOnTheCleanVersion() {
        orderService.placeOrderCleanly("a book");

        assertThat(engine.store().all()).isEmpty();
    }
}
