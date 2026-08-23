package io.github.ohchankyu.puretx.spring;

import io.github.ohchankyu.puretx.PuretxMode;
import io.github.ohchankyu.puretx.PuretxSettings;
import io.github.ohchankyu.puretx.ViolationType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for puretx, under the {@code puretx.*} prefix.
 *
 * <pre>{@code
 * puretx:
 *   mode: WARN          # OFF | WARN | FAIL
 *   max-duration: 3s
 *   ignore:
 *     - com.acme.legacy
 * }</pre>
 *
 * <p>The javadoc on each field is not decoration: {@code spring-boot-configuration-processor}
 * turns it into the description an IDE shows while someone is typing in {@code application.yml}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "puretx")
public class PuretxProperties {

    /** Master switch. When false, puretx adds no interceptors and no listeners at all. */
    private boolean enabled = true;

    /**
     * What to do with a violation. {@code WARN} logs and lets the call through — the right choice
     * for production. {@code FAIL} throws, which is for tests and CI.
     */
    private PuretxMode mode = PuretxMode.WARN;

    /**
     * Transactions open for longer than this are reported. Set to zero or a negative value
     * to turn the duration check off.
     */
    private Duration maxDuration = Duration.ofSeconds(3);

    /**
     * Class or package patterns to stay quiet about, matched against the call site and against the
     * transaction name. {@code com.acme.legacy} covers everything below it; wildcards
     * ({@code com.acme.*.OrderService}, {@code com.acme.**}) also work.
     */
    private List<String> ignore = new ArrayList<>();

    /**
     * Your own packages. Given these, puretx can point at the exact line in your code that caused
     * the violation even when the actual call happens several library frames deeper. Leave empty
     * and it will guess by skipping known infrastructure packages.
     */
    private List<String> appPackages = new ArrayList<>();

    /**
     * Whether to log the chain of application frames that led to the violation.
     *
     * <p>Only your own frames appear, so the path reads as
     * {@code OrderService -> PaymentGateway -> out}, which is the part worth reading.
     */
    private boolean includeCallPath = true;

    /** How many frames to keep when {@code include-call-path} is on. */
    private int callPathDepth = 8;

    /** How many recent violations to keep in memory for {@code Puretx.violations()}. Zero disables. */
    private int recordLimit = 200;

    /** Whether the built-in listener logs violations. Turn off to handle them entirely yourself. */
    private boolean log = true;

    /**
     * Whether to report violations inside the transaction Spring's TestContext framework opens
     * around a {@code @Transactional} test and rolls back afterwards.
     *
     * <p>Off by default. That transaction wraps the whole test method — fixtures, the code under
     * test and the assertions — so it says more about the test harness than about the code. The way
     * to get value from {@code FAIL} in CI is to leave {@code @Transactional} on the service and off
     * the test, so the transaction boundary under test is the real one.
     */
    private boolean detectInTestTransactions = false;

    /** The individual detectors. Final, so Lombok gives it a getter and no setter — which is what binding needs. */
    private final Detectors detectors = new Detectors();

    public PuretxSettings toSettings() {
        return PuretxSettings.builder()
                .enabled(enabled)
                .mode(mode)
                .maxDuration(maxDuration)
                .ignore(ignore)
                .appPackages(appPackages)
                .includeCallPath(includeCallPath)
                .callPathDepth(callPathDepth)
                .recordLimit(recordLimit)
                .detectInTestTransactions(detectInTestTransactions)
                .detect(ViolationType.HTTP_CALL, detectors.isHttp())
                .detect(ViolationType.MESSAGE_PUBLISH, detectors.isMessaging())
                .detect(ViolationType.LONG_TRANSACTION, detectors.isDuration())
                .build();
    }

    /** Individual detectors, all on by default. */
    @Getter
    @Setter
    public static class Detectors {

        /** Outbound HTTP: RestTemplate, RestClient, WebClient, Feign. */
        private boolean http = true;

        /** Message publishing: Kafka. */
        private boolean messaging = true;

        /** Transactions held open beyond {@code puretx.max-duration}. */
        private boolean duration = true;
    }
}
