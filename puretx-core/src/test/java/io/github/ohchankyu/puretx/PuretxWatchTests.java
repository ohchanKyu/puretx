package io.github.ohchankyu.puretx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code watch} is how an application reports what puretx cannot instrument for itself — a vendor
 * SDK that ships its own HTTP stack, or a client built somewhere no bean post-processor can reach.
 */
class PuretxWatchTests {

    private static final TransactionInfo ACTIVE =
            new TransactionInfo("com.acme.orders.OrderService.notify", 40, false, false, "");

    @Test
    @DisplayName("a watched call inside a transaction is reported, and its result is returned")
    void reportsAWatchedCallInsideATransaction() {
        final PuretxEngine engine = install(PuretxMode.WARN, () -> ACTIVE);

        final String result = Puretx.watch("Slack chat.postMessage", () -> "sent");

        assertThat(result).isEqualTo("sent");
        assertThat(engine.store().all()).singleElement().satisfies(violation -> {
            assertThat(violation.type()).isEqualTo(ViolationType.HTTP_CALL);
            assertThat(violation.summary()).isEqualTo("Slack chat.postMessage");
            assertThat(violation.transaction().displayName()).isEqualTo("OrderService.notify");
            assertThat(violation.durationMillis()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("with no transaction open the call runs untouched and nothing is reported")
    void staysOutOfTheWayWithoutATransaction() {
        final PuretxEngine engine = install(PuretxMode.WARN, TransactionProbe.NONE);

        assertThat(Puretx.watch("Slack chat.postMessage", () -> "sent")).isEqualTo("sent");
        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("the call still runs, and is still timed, when it throws")
    void reportsACallThatFailed() {
        final PuretxEngine engine = install(PuretxMode.WARN, () -> ACTIVE);

        assertThatThrownBy(() -> Puretx.watch("Slack chat.postMessage", () -> {
            throw new IllegalStateException("slack is down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("FAIL throws before the call, so a test never reaches the vendor")
    void throwsBeforeTheCallInFailMode() {
        final PuretxEngine engine = install(PuretxMode.FAIL, () -> ACTIVE);
        final AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> Puretx.watch("Slack chat.postMessage", () -> called.set(true)))
                .isInstanceOf(ImpureTransactionException.class);

        assertThat(called).isFalse();
        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("a publish can be watched as one, not as an HTTP call")
    void reportsAWatchedPublish() {
        final PuretxEngine engine = install(PuretxMode.WARN, () -> ACTIVE);

        Puretx.watch(ViolationType.MESSAGE_PUBLISH, "SQS send -> orders", () -> { });

        assertThat(engine.store().all()).singleElement().satisfies(violation ->
                assertThat(violation.type()).isEqualTo(ViolationType.MESSAGE_PUBLISH));
    }

    @Test
    @DisplayName("a watched call inside suppress stays suppressed")
    void respectsSuppression() {
        final PuretxEngine engine = install(PuretxMode.WARN, () -> ACTIVE);

        Puretx.suppress(() -> Puretx.watch("Slack chat.postMessage", () -> "sent"));

        assertThat(engine.store().all()).isEmpty();
    }

    private static PuretxEngine install(final PuretxMode mode, final TransactionProbe probe) {
        final PuretxEngine engine = new PuretxEngine(PuretxSettings.builder().mode(mode).build(), probe);
        Puretx.setEngine(engine);
        return engine;
    }
}
