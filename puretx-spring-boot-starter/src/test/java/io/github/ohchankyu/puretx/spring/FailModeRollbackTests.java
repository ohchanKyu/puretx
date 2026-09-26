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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What FAIL mode does to the transaction it complains about, pinned down per detector.
 *
 * <p>A call is refused before it goes out, and the exception rolls the transaction back like any
 * other. A transaction held too long is a different shape: by the time its length is known it
 * has already committed, so the exception surfaces after the commit and the data stays. There is
 * nothing left to abort, and rolling back a finished transaction for being slow would turn a
 * latency problem into a data problem.
 */
@SpringBootTest(
        classes = PuretxTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"puretx.mode=FAIL", "puretx.max-duration=300ms"})
class FailModeRollbackTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void emptyTable() {
        jdbcTemplate.execute("create table if not exists recorded_orders (item varchar(64))");
        jdbcTemplate.execute("delete from recorded_orders");
        engine.store().clear();
    }

    @Test
    @DisplayName("an impure call rolls back what the transaction wrote before it")
    void impureCallRollsBackEarlierWrites() {
        assertThatThrownBy(() -> orderService.recordThenCharge(server.url()))
                .isInstanceOf(ImpureTransactionException.class);

        assertThat(orderService.recordedOrders()).isZero();
    }

    @Test
    @DisplayName("a transaction held too long fails after its commit, so what it wrote stays")
    void longTransactionFailsAfterCommitAndKeepsItsWrites() {
        assertThatThrownBy(() -> orderService.recordThenLinger(600))
                .isInstanceOf(ImpureTransactionException.class)
                .hasMessageContaining("transaction held past the 300ms limit");

        assertThat(orderService.recordedOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("a slow inner REQUIRES_NEW transaction fails the outer call after both have committed")
    void slowInnerTransactionFailsAfterTheOuterCommit() {
        assertThatThrownBy(() -> orderService.recordThenLingerInNewTransaction(600))
                .isInstanceOf(ImpureTransactionException.class);

        assertThat(orderService.recordedOrders())
                .as("the outer transaction must not roll back around a committed inner one")
                .isEqualTo(2);
        assertThat(engine.store().all()).extracting(violation -> violation.transaction().displayName())
                .as("both were too long, the inner one first; the outer waited for it")
                .containsExactly("InventoryService.recordAndLingerInNewTransaction",
                        "OrderService.recordThenLingerInNewTransaction");
    }
}
