package io.github.ohchankyu.puretx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The log block is the product. If it does not make someone understand the problem in one read,
 * nothing else in this library matters.
 */
class ViolationFormatterTests {

    @Test
    @DisplayName("the report leads with the transaction, then the call, then where it came from")
    void formatsTheFullReport() {
        Violation violation = new Violation(
                ViolationType.HTTP_CALL,
                "HTTP POST https://pay.example.com/charge",
                1180,
                new TransactionInfo("com.acme.orders.OrderService.createOrder", 1204, false, false, ""),
                new StackTraceElement("com.acme.orders.OrderService", "createOrder", "OrderService.java", 47),
                List.of(),
                Instant.EPOCH);

        assertThat(ViolationFormatter.format(violation)).isEqualTo("""
                [puretx] IMPURE TRANSACTION detected
                  tx       : OrderService.createOrder (started 1,204ms ago)
                  violation: HTTP POST https://pay.example.com/charge  (took 1,180ms)
                  at       : com.acme.orders.OrderService.createOrder(OrderService.java:47)
                  hint     : move the call outside the transaction, or defer it past the commit with
                             @TransactionalEventListener(phase = AFTER_COMMIT)""");
    }

    @Test
    @DisplayName("a long transaction reads as a duration, not as an operation")
    void formatsLongTransactions() {
        Violation violation = new Violation(
                ViolationType.LONG_TRANSACTION,
                "transaction held past the 3,000ms limit",
                4102,
                new TransactionInfo("com.acme.orders.OrderService.createOrder", 4102, false, false, "JdbcTransactionManager"),
                null,
                List.of(),
                Instant.EPOCH);

        String report = ViolationFormatter.format(violation);

        assertThat(report).contains("tx       : OrderService.createOrder (open for 4,102ms, JdbcTransactionManager)");
        assertThat(report).contains("violation: transaction held past the 3,000ms limit");
        assertThat(report).doesNotContain("at       :");
    }

    @Test
    @DisplayName("read-only and test-managed transactions say so")
    void annotatesTheTransaction() {
        Violation violation = new Violation(
                ViolationType.HTTP_CALL,
                "HTTP GET https://example.com",
                5,
                new TransactionInfo("com.acme.orders.OrderService.lookUp", 12, true, true, ""),
                null,
                List.of(),
                Instant.EPOCH);

        assertThat(ViolationFormatter.format(violation))
                .contains("(started 12ms ago, read-only, spring test-managed)");
    }

    @Test
    @DisplayName("an unnamed transaction still produces a readable line")
    void handlesUnnamedTransactions() {
        Violation violation = new Violation(
                ViolationType.MESSAGE_PUBLISH,
                "Kafka send -> topic 'orders'",
                -1,
                TransactionInfo.UNKNOWN,
                null,
                List.of(),
                Instant.EPOCH);

        String report = ViolationFormatter.format(violation);

        assertThat(report).contains("tx       : <unnamed transaction>");
        assertThat(report).contains("violation: Kafka send -> topic 'orders'");
        assertThat(report).doesNotContain("took");
    }

    @Test
    @DisplayName("the call path is appended only when one was captured")
    void appendsTheCallPathWhenPresent() {
        StackTraceElement frame =
                new StackTraceElement("com.acme.orders.OrderService", "createOrder", "OrderService.java", 47);
        Violation violation = new Violation(
                ViolationType.HTTP_CALL, "HTTP GET https://example.com", 5,
                TransactionInfo.UNKNOWN, frame, List.of(frame), Instant.EPOCH);

        assertThat(ViolationFormatter.formatWithCallPath(violation))
                .contains("path     :")
                .contains("at com.acme.orders.OrderService.createOrder(OrderService.java:47)");
    }
}
