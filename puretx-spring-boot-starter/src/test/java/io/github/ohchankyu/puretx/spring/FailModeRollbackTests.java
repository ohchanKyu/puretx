package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxTestApplication;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.ImpureTransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FAIL mode aborts the unit of work it complains about, and the README says so. These pin that
 * down: a row written before the violation does not survive it, whichever detector fired.
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

    @BeforeEach
    void emptyTable() {
        jdbcTemplate.execute("create table if not exists recorded_orders (item varchar(64))");
        jdbcTemplate.execute("delete from recorded_orders");
    }

    @Test
    @DisplayName("an impure call rolls back what the transaction wrote before it")
    void impureCallRollsBackEarlierWrites() {
        assertThatThrownBy(() -> orderService.recordThenCharge(server.url()))
                .isInstanceOf(ImpureTransactionException.class);

        assertThat(orderService.recordedOrders()).isZero();
    }

    @Test
    @DisplayName("a transaction held too long fails at commit, and what it wrote rolls back with it")
    void longTransactionFailsAtCommitAndRollsBack() {
        assertThatThrownBy(() -> orderService.recordThenLinger(600))
                .isInstanceOf(ImpureTransactionException.class)
                .hasMessageContaining("transaction held past the 300ms limit");

        assertThat(orderService.recordedOrders()).isZero();
    }
}
