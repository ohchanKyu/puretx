package io.github.ohchankyu.puretx.spring.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ohchankyu.puretx.TransactionInfo;
import io.github.ohchankyu.puretx.TransactionSummary;
import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Metrics turn WARN mode into something you can put on a dashboard, rather than a log to grep. */
class PuretxMetricsListenerTests {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("a violation is counted and its duration recorded, tagged by type")
    void countsAndTimesAViolation() {
        new PuretxMetricsListener(() -> registry).onViolation(violation(ViolationType.HTTP_CALL, 431));

        assertThat(registry.counter(PuretxMetricsListener.COUNT, "type", "http").count()).isEqualTo(1);
        assertThat(registry.timer(PuretxMetricsListener.DURATION, "type", "http")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(431);
    }

    @Test
    @DisplayName("types are counted separately, so a dashboard can tell them apart")
    void separatesTypes() {
        final PuretxMetricsListener listener = new PuretxMetricsListener(() -> registry);

        listener.onViolation(violation(ViolationType.HTTP_CALL, 10));
        listener.onViolation(violation(ViolationType.HTTP_CALL, 20));
        listener.onViolation(violation(ViolationType.MESSAGE_PUBLISH, Violation.UNKNOWN_DURATION));

        assertThat(registry.counter(PuretxMetricsListener.COUNT, "type", "http").count()).isEqualTo(2);
        assertThat(registry.counter(PuretxMetricsListener.COUNT, "type", "messaging").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a violation with no duration is counted but not timed")
    void doesNotTimeWhatItCannotTime() {
        new PuretxMetricsListener(() -> registry)
                .onViolation(violation(ViolationType.MESSAGE_PUBLISH, Violation.UNKNOWN_DURATION));

        assertThat(registry.counter(PuretxMetricsListener.COUNT, "type", "messaging").count()).isEqualTo(1);
        assertThat(registry.find(PuretxMetricsListener.DURATION).timer()).isNull();
    }

    @Test
    @DisplayName("no registry, no work — the listener is harmless on its own")
    void doesNothingWithoutARegistry() {
        new PuretxMetricsListener(() -> null).onViolation(violation(ViolationType.HTTP_CALL, 10));

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("a transaction summary publishes the wait and the share it took")
    void publishesTheTransactionSummary() {
        new PuretxMetricsListener(() -> registry)
                .onTransactionSummary(new TransactionSummary("com.acme.OrderService.placeOrder", 400, 2, 380));

        assertThat(registry.timer(PuretxMetricsListener.WAIT).totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(380);
        assertThat(registry.summary(PuretxMetricsListener.SHARE).max()).isEqualTo(95);
    }

    @Test
    @DisplayName("the summary meters carry no tags, so there is no cardinality to weigh up")
    void keepsTheSummaryMetersUntagged() {
        new PuretxMetricsListener(() -> registry)
                .onTransactionSummary(new TransactionSummary("com.acme.OrderService.placeOrder", 400, 1, 100));

        assertThat(registry.find(PuretxMetricsListener.WAIT).timer().getId().getTags()).isEmpty();
    }

    private static Violation violation(final ViolationType type, final long durationMillis) {
        return new Violation(type, "HTTP GET https://example.com", durationMillis,
                new TransactionInfo("com.acme.OrderService.createOrder", 40, false, false, ""),
                null, List.of(), Instant.EPOCH);
    }
}
