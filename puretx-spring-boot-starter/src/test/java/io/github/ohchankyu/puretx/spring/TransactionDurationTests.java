package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxTestApplication;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A transaction can be impure by doing nothing at all, for too long. */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "puretx.max-duration=100ms")
class TransactionDurationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void reset() {
        engine.store().clear();
    }

    @Test
    @DisplayName("a transaction held past the threshold is reported once, at commit")
    void reportsTransactionsOverTheThreshold() {
        orderService.slowOrder(250);

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.LONG_TRANSACTION);
            assertThat(violation.transaction().displayName()).isEqualTo("OrderService.slowOrder");
            assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(250);
            assertThat(violation.summary()).isEqualTo("transaction held past the 100ms limit");
        });
    }

    @Test
    @DisplayName("a rolled-back transaction is still reported, without piling an exception onto the failure")
    void reportsRolledBackTransactionsWithoutThrowing() {
        assertThatThrownBy(() -> orderService.failingSlowOrder(250))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.type()).isEqualTo(ViolationType.LONG_TRANSACTION));
    }

    @Test
    @DisplayName("a transaction under the threshold stays silent")
    void ignoresFastTransactions() {
        orderService.slowOrder(1);

        assertThat(engine.store().all()).isEmpty();
    }
}
