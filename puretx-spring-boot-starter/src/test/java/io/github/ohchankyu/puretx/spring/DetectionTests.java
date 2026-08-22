package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.orders.OrderService;
import com.acme.orders.PaymentClient;
import com.acme.orders.PuretxIntegrationTest;
import com.acme.orders.StubHttpServer;
import io.github.ohchankyu.puretx.PuretxEngine;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The cases puretx exists to catch. */
@PuretxIntegrationTest
class DetectionTests {

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
    @DisplayName("an HTTP call inside @Transactional is reported with the transaction it interrupted")
    void reportsHttpCallInsideTransaction() {
        orderService.createOrder(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.summary()).startsWith("HTTP GET").contains("/charge");
            assertThat(violation.transaction().name())
                    .isEqualTo(OrderService.class.getName() + ".createOrder");
            assertThat(violation.transaction().displayName()).isEqualTo("OrderService.createOrder");
            assertThat(violation.transaction().hasElapsed()).isTrue();
            assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("the call site is the frame that made the call, not the framework that carried it")
    void reportsTheApplicationCallSite() {
        orderService.createOrder(server.url());

        Violation violation = engine.store().all().get(0);
        assertThat(violation.origin()).isNotNull();
        // PaymentClient is a helper between OrderService and RestTemplate: this indirection is
        // precisely what a class-level dependency rule cannot follow.
        assertThat(violation.origin().getClassName()).isEqualTo(PaymentClient.class.getName());
        assertThat(violation.callPath()).extracting(StackTraceElement::getClassName)
                .containsExactly(PaymentClient.class.getName(), OrderService.class.getName());
    }

    @Test
    @DisplayName("a BEFORE_COMMIT event handler still runs inside the transaction")
    void reportsBeforeCommitEventListeners() {
        orderService.createOrderPublishingBeforeCommitEvent(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL));
    }

    @Test
    @DisplayName("REQUIRES_NEW gets its own transaction, and the outer one comes back afterwards")
    void attributesCallsToTheInnermostTransaction() {
        orderService.createOrderWithSeparateAudit(server.url());

        List<String> transactions = engine.store().all().stream()
                .map(violation -> violation.transaction().displayName())
                .toList();

        assertThat(transactions).containsExactly(
                "InventoryService.reserveInNewTransaction",
                "OrderService.createOrderWithSeparateAudit");
    }

    @Test
    @DisplayName("a read-only transaction is flagged as such in the report")
    void marksReadOnlyTransactions() {
        orderService.readOnlyLookup(server.url());

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.transaction().readOnly()).isTrue());
    }

    @Test
    @DisplayName("violations are readable from the static facade too")
    void exposesViolationsThroughTheFacade() {
        orderService.createOrder(server.url());

        assertThat(engine.store().total()).isEqualTo(1);
        assertThatThrownBy(() -> {
            throw new IllegalStateException(engine.store().all().get(0).toString());
        }).hasMessageContaining("[puretx]").hasMessageContaining("OrderService.createOrder");
    }
}
