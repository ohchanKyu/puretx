package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.util.CollectionUtils;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable configuration for a {@link PuretxEngine}. */
public final class PuretxSettings {

    private final boolean enabled;

    private final PuretxMode mode;

    private final Duration maxDuration;

    private final List<String> ignore;

    private final List<String> appPackages;

    private final boolean includeCallPath;

    private final int callPathDepth;

    private final int recordLimit;

    private final boolean detectInTestTransactions;

    private final Set<ViolationType> detectors;

    private final long maxDurationMillis;

    private final boolean durationCheckEnabled;

    private PuretxSettings(final Builder b) {
        this.enabled = b.enabled;
        this.mode = b.mode;
        this.maxDuration = b.maxDuration;
        this.ignore = List.copyOf(b.ignore);
        this.appPackages = List.copyOf(b.appPackages);
        this.includeCallPath = b.includeCallPath;
        this.callPathDepth = Math.max(1, b.callPathDepth);
        this.recordLimit = b.recordLimit;
        this.detectInTestTransactions = b.detectInTestTransactions;
        this.detectors = EnumSet.copyOf(b.detectors);
        this.maxDurationMillis = maxDuration == null ? -1 : maxDuration.toMillis();
        this.durationCheckEnabled = detectors.contains(ViolationType.LONG_TRANSACTION)
                && this.maxDurationMillis > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Defaults: enabled, {@code WARN}, 3s duration threshold, every detector on. */
    public static PuretxSettings defaults() {
        return builder().build();
    }

    public static PuretxSettings off() {
        return builder().enabled(false).mode(PuretxMode.OFF).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public PuretxMode mode() {
        return mode;
    }

    /** @return the threshold above which a transaction is reported, or {@code null} when disabled */
    public Duration maxDuration() {
        return maxDuration;
    }

    public List<String> ignore() {
        return ignore;
    }

    public List<String> appPackages() {
        return appPackages;
    }

    public boolean includeCallPath() {
        return includeCallPath;
    }

    public int callPathDepth() {
        return callPathDepth;
    }

    public int recordLimit() {
        return recordLimit;
    }

    public boolean detectInTestTransactions() {
        return detectInTestTransactions;
    }

    public boolean detects(final ViolationType type) {
        return detectors.contains(type);
    }

    /** True when nothing at all should be done — the cheapest possible check. */
    public boolean idle() {
        return !enabled || mode == PuretxMode.OFF || detectors.isEmpty();
    }

    public long maxDurationMillis() {
        return maxDurationMillis;
    }

    /**
     * One line for the startup log.
     *
     * <p>A detection library that is silent is ambiguous: it could mean there is nothing to report,
     * or it could mean the library never got wired up at all. Saying so once at startup removes
     * that ambiguity, and names the properties that would change the answer.
     */
    public String describe() {
        if (!enabled) {
            return "disabled (puretx.enabled=false)";
        }
        if (mode == PuretxMode.OFF) {
            return "disabled (puretx.mode=OFF)";
        }
        if (detectors.isEmpty()) {
            return "disabled (every puretx.detectors.* is off)";
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append("watching transactions — mode=").append(mode);
        if (durationCheckEnabled()) {
            sb.append(", max-duration=").append(format(maxDuration));
        }
        return sb.append(", detectors=")
                .append(detectors.stream().map(ViolationType::configKey)
                        .collect(Collectors.joining(", ", "[", "]")))
                .toString();
    }

    private static String format(final Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? (millis / 1000) + "s" : millis + "ms";
    }

    public boolean durationCheckEnabled() {
        return durationCheckEnabled;
    }

    public static final class Builder {

        private boolean enabled = true;
        private PuretxMode mode = PuretxMode.WARN;
        private Duration maxDuration = Duration.ofSeconds(3);
        private List<String> ignore = List.of();
        private List<String> appPackages = List.of();
        private boolean includeCallPath = true;
        private int callPathDepth = 8;
        private int recordLimit = 200;
        private boolean detectInTestTransactions = false;
        private Set<ViolationType> detectors = EnumSet.allOf(ViolationType.class);

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder mode(final PuretxMode mode) {
            this.mode = mode == null ? PuretxMode.OFF : mode;
            return this;
        }

        public Builder maxDuration(final Duration maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public Builder ignore(final List<String> ignore) {
            this.ignore = CollectionUtils.isEmpty(ignore) ? List.of() : ignore;
            return this;
        }

        public Builder appPackages(final List<String> appPackages) {
            this.appPackages = CollectionUtils.isEmpty(appPackages) ? List.of() : appPackages;
            return this;
        }

        public Builder includeCallPath(final boolean includeCallPath) {
            this.includeCallPath = includeCallPath;
            return this;
        }

        public Builder callPathDepth(final int callPathDepth) {
            this.callPathDepth = callPathDepth;
            return this;
        }

        public Builder recordLimit(final int recordLimit) {
            this.recordLimit = recordLimit;
            return this;
        }

        public Builder detectInTestTransactions(final boolean detectInTestTransactions) {
            this.detectInTestTransactions = detectInTestTransactions;
            return this;
        }

        public Builder detectors(final Set<ViolationType> detectors) {
            this.detectors = CollectionUtils.isEmpty(detectors)
                    ? EnumSet.noneOf(ViolationType.class)
                    : EnumSet.copyOf(detectors);
            return this;
        }

        public Builder detect(final ViolationType type, final boolean on) {
            if (on) {
                detectors.add(type);
            } else {
                detectors.remove(type);
            }
            return this;
        }

        public PuretxSettings build() {
            return new PuretxSettings(this);
        }
    }
}
