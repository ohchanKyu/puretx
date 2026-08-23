package io.github.ohchankyu.puretx.spring.metrics;

import io.github.ohchankyu.puretx.Violation;
import io.github.ohchankyu.puretx.ViolationListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.util.function.SingletonSupplier;

/**
 * Publishes every violation as a metric, so {@code WARN} mode in production is more than a log to grep.
 *
 * <p>Two meters, both tagged only by violation type:
 *
 * <ul>
 *   <li>{@code puretx.violations} — how often, so a trend is visible on a dashboard
 *   <li>{@code puretx.violation.duration} — how long the offending operation took, which adds up to
 *       the time transactions spent waiting on something outside the database
 * </ul>
 *
 * <p>The call site and the transaction name are deliberately not tags. Both are unbounded from the
 * registry's point of view, and a metrics backend charges for cardinality. The log line already
 * names them; metrics are for the trend, logs are for the detail.
 */
public final class PuretxMetricsListener implements ViolationListener {

    static final String COUNT = "puretx.violations";

    static final String DURATION = "puretx.violation.duration";

    private static final String TYPE_TAG = "type";

    private final Supplier<MeterRegistry> registry;

    public PuretxMetricsListener(final Supplier<MeterRegistry> registry) {
        this.registry = SingletonSupplier.of(registry);
    }

    @Override
    public void onViolation(final Violation violation) {
        final MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        final String type = violation.type().configKey();
        meters.counter(COUNT, TYPE_TAG, type).increment();
        if (violation.hasDuration()) {
            meters.timer(DURATION, TYPE_TAG, type)
                    .record(violation.durationMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
