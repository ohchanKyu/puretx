package io.github.ohchankyu.puretx.spring;

import io.github.ohchankyu.puretx.Puretx;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Counts what actually got instrumented, and says so once the context is up.
 *
 * <p>The startup line above this one reports which detectors are switched on, which is not the
 * same question as whether any of them are attached to anything. An application can log
 * {@code detectors=[http]} and have no instrumented client at all, and the only symptom is
 * silence — indistinguishable from clean code.
 *
 * <p>Registrations are counted when this runs, not when they were made. Attaching and counting in
 * the same breath is how the report came to claim a Kafka producer factory that had silently
 * dropped the registration: {@code addPostProcessor} is an interface default with an empty body.
 * Anything that can be undone later — a listener list replaced wholesale, a post-processor a
 * factory chose to ignore — hands over a check instead of a number, and is counted only if it is
 * still attached by the time every singleton exists.
 *
 * <p>It warns about the two shapes that mean nothing will ever be reported, and about nothing
 * else. Listing a kind an application simply does not use would be noise, and a warning that
 * fires on a correct setup is how a library gets removed.
 */
public final class InstrumentationReport implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    /** Kinds that carry an outbound call, as opposed to opening the transaction around one. */
    private static final List<String> HTTP_KINDS = List.of("RestTemplate", "RestClient", "WebClient", "WebClient.Builder", "Feign");

    private static final String TRANSACTION_MANAGER = "transaction manager";

    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    private Map<String, Long> counts = Map.of();

    private volatile boolean watchingHttp;

    /**
     * Records an instrumentation that cannot come undone — puretx built the object being returned,
     * so nothing else gets to replace what was put in it.
     */
    public void instrumented(final String kind) {
        instrumented(kind, () -> true);
    }

    /**
     * Records an instrumentation that something else could still remove, along with how to tell.
     *
     * @param stillAttached evaluated once every singleton exists; a registration that has since
     *                      been dropped is not counted, and not used to suppress a warning
     */
    public void instrumented(final String kind, final BooleanSupplier stillAttached) {
        registrations.add(new Registration(kind, stillAttached));
    }

    /** Declared by the HTTP detectors when they are configured at all. */
    public void watchingHttp() {
        this.watchingHttp = true;
    }

    @Override
    public void afterSingletonsInstantiated() {
        counts = registrations.stream()
                .filter(registration -> registration.stillAttached().getAsBoolean())
                .collect(Collectors.groupingBy(Registration::kind, Collectors.counting()));

        final String tally = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining(", "));

        if (count(TRANSACTION_MANAGER) == 0) {
            log.warn("[puretx] no transaction manager was instrumented, so no transaction is visible "
                    + "and nothing will ever be reported. Only an AbstractPlatformTransactionManager "
                    + "can be instrumented; reactive transaction managers cannot.");
            return;
        }
        if (watchingHttp && HTTP_KINDS.stream().mapToLong(this::count).sum() == 0) {
            log.warn("[puretx] instrumented {}, but no HTTP client — an outbound call is only seen "
                    + "through a RestTemplate, RestClient or WebClient bean, so a client built inside "
                    + "a method, or a vendor SDK with its own stack, stays invisible.", tally);
            return;
        }
        if (!tally.isEmpty()) {
            log.info("[puretx] instrumented {}", tally);
        }
    }

    private long count(final String kind) {
        return counts.getOrDefault(kind, 0L);
    }

    private record Registration(String kind, BooleanSupplier stillAttached) { }
}
