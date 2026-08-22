package io.github.ohchankyu.puretx.spring;

import io.github.ohchankyu.puretx.Puretx;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>It warns about the two shapes that mean nothing will ever be reported, and about nothing
 * else. Listing a kind an application simply does not use would be noise, and a warning that
 * fires on a correct setup is how a library gets removed.
 */
public final class InstrumentationReport implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    /** Kinds that carry an outbound call, as opposed to opening the transaction around one. */
    private static final String[] HTTP_KINDS = {"RestTemplate", "RestClient", "WebClient.Builder"};

    private static final String TRANSACTION_MANAGER = "transaction manager";

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    private volatile boolean watchingHttp;

    /** Called by each post-processor as it attaches puretx to a bean. */
    public void instrumented(final String kind) {
        counts.computeIfAbsent(kind, key -> new AtomicInteger()).incrementAndGet();
    }

    /** Declared by the HTTP detectors when they are configured at all. */
    public void watchingHttp() {
        this.watchingHttp = true;
    }

    @Override
    public void afterSingletonsInstantiated() {
        final String tally = counts.entrySet().stream()
                .filter(entry -> entry.getValue().get() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().get() + " " + entry.getKey())
                .collect(Collectors.joining(", "));

        if (count(TRANSACTION_MANAGER) == 0) {
            log.warn("[puretx] no transaction manager was instrumented, so no transaction is visible "
                    + "and nothing will ever be reported. Only an AbstractPlatformTransactionManager "
                    + "can be instrumented; reactive transaction managers cannot.");
            return;
        }
        if (watchingHttp && httpInstrumented() == 0) {
            log.warn("[puretx] instrumented {}, but no HTTP client — an outbound call is only seen "
                    + "through a RestTemplate, RestClient or WebClient bean, so a client built inside "
                    + "a method, or a vendor SDK with its own stack, stays invisible.", tally);
            return;
        }
        if (!tally.isEmpty()) {
            log.info("[puretx] instrumented {}", tally);
        }
    }

    private int httpInstrumented() {
        int total = 0;
        for (final String kind : HTTP_KINDS) {
            total += count(kind);
        }
        return total;
    }

    private int count(final String kind) {
        final AtomicInteger value = counts.get(kind);
        return value == null ? 0 : value.get();
    }
}
