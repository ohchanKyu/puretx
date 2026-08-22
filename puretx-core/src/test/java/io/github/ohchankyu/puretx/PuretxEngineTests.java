package io.github.ohchankyu.puretx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The decision logic, with the framework taken out of the picture. */
class PuretxEngineTests {

    private static final TransactionInfo ACTIVE =
            new TransactionInfo("com.acme.orders.OrderService.createOrder", 1204, false, false, "JdbcTransactionManager");

    @Test
    @DisplayName("no transaction, no violation")
    void reportsNothingWithoutATransaction() {
        PuretxEngine engine = engine(PuretxSettings.builder().build(), TransactionProbe.NONE);

        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("OFF does no work at all")
    void staysOutOfTheWayWhenOff() {
        PuretxEngine engine = engine(PuretxSettings.builder().mode(PuretxMode.OFF).build(), () -> ACTIVE);

        assertThat(engine.isWatching(ViolationType.HTTP_CALL)).isFalse();
        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");
        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("WARN records and returns, FAIL records and throws")
    void modeDecidesWhetherToThrow() {
        PuretxEngine warn = engine(PuretxSettings.builder().mode(PuretxMode.WARN).build(), () -> ACTIVE);
        warn.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");
        assertThat(warn.store().all()).hasSize(1);

        PuretxEngine fail = engine(PuretxSettings.builder().mode(PuretxMode.FAIL).build(), () -> ACTIVE);
        assertThatThrownBy(() -> fail.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com"))
                .isInstanceOf(ImpureTransactionException.class);
        assertThat(fail.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("reportQuietly records in FAIL mode but keeps the exception to itself")
    void quietReportingNeverThrows() {
        PuretxEngine engine = engine(PuretxSettings.builder().mode(PuretxMode.FAIL).build(), () -> ACTIVE);

        engine.reportLongTransaction(4000, true);

        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("an ignore pattern can match the transaction name as well as the call site")
    void ignoreMatchesTransactionName() {
        PuretxEngine engine = engine(
                PuretxSettings.builder().ignore(List.of("com.acme.orders")).build(), () -> ACTIVE);

        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

        assertThat(engine.store().all()).isEmpty();
    }

    @Test
    @DisplayName("suppression is re-entrant and survives an exception")
    void suppressionIsReentrant() {
        PuretxEngine engine = engine(PuretxSettings.builder().build(), () -> ACTIVE);
        Puretx.setEngine(engine);

        assertThatThrownBy(() -> Puretx.suppress(() -> {
            Puretx.suppress(() -> engine.report(ViolationType.HTTP_CALL, () -> "inner"));
            engine.report(ViolationType.HTTP_CALL, () -> "outer");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(engine.store().all()).isEmpty();
        assertThat(Puretx.isSuppressed()).isFalse();

        engine.report(ViolationType.HTTP_CALL, () -> "after");
        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("a test-managed transaction is skipped unless explicitly asked for")
    void skipsTestManagedTransactionsByDefault() {
        TransactionInfo testTransaction = new TransactionInfo("SomeTest.method", 10, false, true, "");

        PuretxEngine off = engine(PuretxSettings.builder().build(), () -> testTransaction);
        off.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");
        assertThat(off.store().all()).isEmpty();

        PuretxEngine on = engine(
                PuretxSettings.builder().detectInTestTransactions(true).build(), () -> testTransaction);
        on.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");
        assertThat(on.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("the store keeps the most recent violations and counts the rest")
    void storeIsBounded() {
        PuretxEngine engine = engine(PuretxSettings.builder().recordLimit(2).build(), () -> ACTIVE);

        for (int i = 0; i < 5; i++) {
            final String summary = "call " + i;
            engine.report(ViolationType.HTTP_CALL, () -> summary);
        }

        assertThat(engine.store().all()).extracting(Violation::summary).containsExactly("call 3", "call 4");
        assertThat(engine.store().total()).isEqualTo(5);
    }

    @Test
    @DisplayName("a listener that throws does not take the application down with it")
    void listenerFailuresAreContained() {
        PuretxEngine engine = engine(PuretxSettings.builder().build(), () -> ACTIVE);
        engine.addListener(violation -> {
            throw new IllegalStateException("bad listener");
        });

        engine.report(ViolationType.HTTP_CALL, () -> "HTTP GET https://example.com");

        assertThat(engine.store().all()).hasSize(1);
    }

    @Test
    @DisplayName("the startup line says what is on, and names the property that would turn it off")
    void describesItselfForTheStartupLog() {
        assertThat(PuretxSettings.defaults().describe())
                .isEqualTo("watching transactions — mode=WARN, max-duration=3s, "
                        + "detectors=[http, messaging, duration]");

        assertThat(PuretxSettings.builder().maxDuration(Duration.ofMillis(300)).build().describe())
                .contains("max-duration=300ms");

        assertThat(PuretxSettings.builder().detect(ViolationType.MESSAGE_PUBLISH, false).build().describe())
                .contains("detectors=[http, duration]");
    }

    @Test
    @DisplayName("a switched-off puretx says which switch did it")
    void describesWhyItIsOff() {
        assertThat(PuretxSettings.builder().enabled(false).build().describe())
                .isEqualTo("disabled (puretx.enabled=false)");
        assertThat(PuretxSettings.builder().mode(PuretxMode.OFF).build().describe())
                .isEqualTo("disabled (puretx.mode=OFF)");
        assertThat(PuretxSettings.builder().detectors(java.util.Set.of()).build().describe())
                .isEqualTo("disabled (every puretx.detectors.* is off)");
    }

    private static PuretxEngine engine(final PuretxSettings settings, final TransactionProbe probe) {
        return new PuretxEngine(settings, probe);
    }
}
