package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.acme.orders.OrderService;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.TransactionSummary;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationListener;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The per-call reports say a transaction was interrupted; the summary says how much of it was the
 * interruption. That share is the number nothing else can produce.
 */
@PuretxIntegrationTest
class TransactionRollupTests {

    private final List<TransactionSummary> summaries = new CopyOnWriteArrayList<>();

    private final ViolationListener collector = new ViolationListener() {
        @Override
        public void onViolation(final Violation violation) {
        }

        @Override
        public void onTransactionSummary(final TransactionSummary summary) {
            summaries.add(summary);
        }
    };

    @Autowired
    private OrderService orderService;

    @Autowired
    private StubHttpServer server;

    @Autowired
    private PuretxEngine engine;

    @BeforeEach
    void reset() {
        engine.store().clear();
        summaries.clear();
        engine.addListener(collector);
    }

    @AfterEach
    void detach() {
        engine.removeListener(collector);
    }

    @Test
    @DisplayName("a transaction is summarised once, whatever the transaction did inside it")
    void summarisesTheTransactionOnce() {
        server.setDelayMillis(60);
        try {
            orderService.createOrder(server.url());
        } finally {
            server.setDelayMillis(0);
        }

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.displayName()).isEqualTo("OrderService.createOrder");
            assertThat(summary.callCount()).isEqualTo(1);
            assertThat(summary.callMillis()).isGreaterThanOrEqualTo(60);
            assertThat(summary.transactionMillis()).isGreaterThanOrEqualTo(summary.callMillis());
            assertThat(summary.percentageSpentOnCalls()).isPositive();
        });
    }

    @Test
    @DisplayName("a WebClient call is attributed too, though it is recorded on another thread")
    void attributesCallsRecordedOnAnotherThread() {
        orderService.createOrderWithWebClient(server.url());

        // The scope travels with the violation, so the thread it lands on does not matter — and
        // a call recorded after the transaction ended still produces the summary.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(summaries).singleElement().satisfies(summary -> {
                    assertThat(summary.displayName()).isEqualTo("OrderService.createOrderWithWebClient");
                    assertThat(summary.callCount()).isEqualTo(1);
                }));
    }

    @Test
    @DisplayName("a call reported through Puretx.watch counts toward the summary like any other")
    void countsWatchedCallsToo() {
        orderService.createOrderWithWatchedSdkCall();

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.displayName()).isEqualTo("OrderService.createOrderWithWatchedSdkCall");
            assertThat(summary.callCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("a transaction that did nothing wrong is not summarised at all")
    void staysQuietWhenThereIsNothingToExplain() {
        orderService.createOrderChargingAfterCommit(server.url());

        assertThat(summaries).isEmpty();
    }

    @Test
    @DisplayName("the long-transaction report is not counted as a call inside itself")
    void doesNotCountTheDurationReportAsACall() {
        orderService.createOrderWithSeparateAudit(server.url());

        assertThat(summaries).allSatisfy(summary ->
                assertThat(summary.callCount()).isPositive());
        assertThat(summaries).extracting(TransactionSummary::displayName)
                .containsExactly("InventoryService.reserveInNewTransaction",
                        "OrderService.createOrderWithSeparateAudit");
    }
}
