package io.github.ohchankyu.puretx.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.ohchankyu.puretx.Puretx;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The report exists so that "nothing to report" is distinguishable from "attached to nothing".
 *
 * <p>Knowing which detectors are switched on is not the same as knowing they reached anything:
 * an application can log {@code detectors=[http]} and have no instrumented client at all.
 */
class InstrumentationReportTests {

    @Test
    @DisplayName("what was instrumented is listed once the context is up")
    void listsWhatWasInstrumented() {
        final List<ILoggingEvent> events = capture();
        final InstrumentationReport report = new InstrumentationReport();
        report.watchingHttp();
        report.instrumented("RestClient");
        report.instrumented("RestClient");
        report.instrumented("transaction manager");

        report.afterSingletonsInstantiated();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo(
                    "[puretx] instrumented 2 RestClient, 1 transaction manager");
        });
    }

    @Test
    @DisplayName("watching HTTP but reaching no client is a warning: nothing will ever be reported")
    void warnsWhenHttpDetectionReachedNoClient() {
        final List<ILoggingEvent> events = capture();
        final InstrumentationReport report = new InstrumentationReport();
        report.watchingHttp();
        report.instrumented("transaction manager");

        report.afterSingletonsInstantiated();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("no HTTP client");
        });
    }

    @Test
    @DisplayName("no transaction manager is the one failure that makes everything else moot")
    void warnsWhenNoTransactionManagerWasInstrumented() {
        final List<ILoggingEvent> events = capture();
        final InstrumentationReport report = new InstrumentationReport();
        report.instrumented("RestTemplate");

        report.afterSingletonsInstantiated();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("no transaction manager");
        });
    }

    @Test
    @DisplayName("a kind the application simply does not use is not mentioned at all")
    void staysQuietAboutKindsTheApplicationDoesNotUse() {
        final List<ILoggingEvent> events = capture();
        final InstrumentationReport report = new InstrumentationReport();
        report.watchingHttp();
        report.instrumented("transaction manager");
        report.instrumented("RestTemplate");

        report.afterSingletonsInstantiated();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("[puretx] instrumented 1 RestTemplate, 1 transaction manager")
                    .doesNotContain("RestClient", "WebClient");
        });
    }

    @Test
    @DisplayName("an application whose only client is Feign is not warned at")
    void countsFeignAsAnInstrumentedHttpClient() {
        final List<ILoggingEvent> events = capture();
        final InstrumentationReport report = new InstrumentationReport();
        report.watchingHttp();
        report.instrumented("Feign");
        report.instrumented("transaction manager");

        report.afterSingletonsInstantiated();

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getLevel()).isEqualTo(Level.INFO));
    }

    private static List<ILoggingEvent> capture() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        final ch.qos.logback.classic.Logger logger = context.getLogger(Puretx.LOGGER_NAME);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender.list;
    }
}
