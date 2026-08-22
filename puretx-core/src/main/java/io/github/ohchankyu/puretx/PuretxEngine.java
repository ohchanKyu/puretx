package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.PackagePatterns;
import io.github.ohchankyu.puretx.internal.StackCapture;
import io.github.ohchankyu.puretx.internal.Suppressions;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The part of puretx that decides whether something is a violation, and what to do about it.
 *
 * <p>Detectors call {@link #start} before the suspicious operation and {@link #finish} after it.
 * Everything expensive — walking the stack, formatting, matching ignore patterns — happens only
 * once {@code start} has established that a transaction really is open, so the cost on a
 * non-transactional call path is a couple of field reads.
 */
public final class PuretxEngine {

    private volatile PuretxSettings settings;
    private volatile PackagePatterns ignore;
    private volatile PackagePatterns appPackages;
    private volatile TransactionProbe probe;

    private final ViolationStore store;
    private final List<ViolationListener> listeners = new CopyOnWriteArrayList<>();

    public PuretxEngine(final PuretxSettings settings, final TransactionProbe probe) {
        this.store = new ViolationStore(settings.recordLimit());
        this.probe = probe == null ? TransactionProbe.NONE : probe;
        applySettings(settings);
    }

    /** An engine that will never report anything — the default before Spring wires the real one up. */
    public static PuretxEngine disabled() {
        return new PuretxEngine(PuretxSettings.off(), TransactionProbe.NONE);
    }

    public PuretxSettings settings() {
        return settings;
    }

    public void applySettings(final PuretxSettings settings) {
        this.settings = settings;
        this.ignore = PackagePatterns.of(settings.ignore());
        this.appPackages = PackagePatterns.of(settings.appPackages());
    }

    public void setProbe(final TransactionProbe probe) {
        this.probe = probe == null ? TransactionProbe.NONE : probe;
    }

    public TransactionProbe probe() {
        return probe;
    }

    public ViolationStore store() {
        return store;
    }

    public void addListener(final ViolationListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(final ViolationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Cheap pre-check: {@code false} means {@link #start} cannot possibly report anything.
     *
     * <p>Note what this does <em>not</em> check: whether a transaction is open. Detectors should
     * pass their summary to {@link #start} as a supplier rather than gating on this, so that the
     * string is never built on the overwhelmingly common path where nothing is wrong.
     */
    public boolean isWatching(final ViolationType type) {
        return isWatching(settings, type);
    }

    private static boolean isWatching(final PuretxSettings s, final ViolationType type) {
        return s.enabled() && s.mode().isActive() && s.detects(type) && !Suppressions.active();
    }

    /**
     * @param summary describes the operation, e.g. {@code HTTP POST https://pay.example.com/charge}.
     *                Resolved only once a transaction has been found, so building it costs nothing
     *                on the path where there is no violation
     * @return a token to hand to {@link #finish}, or {@code null} if this is not a violation
     * @throws ImpureTransactionException in {@link PuretxMode#FAIL}, before the operation runs
     */
    public Detection start(final ViolationType type, final Supplier<String> summary) {
        final PuretxSettings s = settings;
        if (!isWatching(s, type)) {
            return null;
        }
        final TransactionInfo tx = probe.currentTransaction();
        if (tx == null) {
            return null;
        }
        if (tx.testManaged() && !s.detectInTestTransactions()) {
            return null;
        }
        // Cheap half of the ignore check first: a transaction whose name is ignored should never
        // pay for a stack walk.
        final PackagePatterns patterns = ignore;
        if (tx.hasName() && patterns.matches(tx.name())) {
            return null;
        }
        final StackCapture.Result capture = type.reportsCallSite()
                ? StackCapture.capture(appPackages, s.includeCallPath(), s.callPathDepth())
                : StackCapture.Result.EMPTY;
        if (capture.origin() != null && patterns.matches(capture.origin().getClassName())) {
            return null;
        }
        final Detection detection =
                new Detection(type, summary.get(), tx, capture.origin(), capture.callPath());
        if (s.mode() == PuretxMode.FAIL) {
            // Report before the call happens: in a test we would rather not make it at all.
            throw new ImpureTransactionException(
                    record(detection.toViolation(Violation.UNKNOWN_DURATION, Instant.now())));
        }
        return detection;
    }

    /**
     * Records the detection started earlier, timed from when {@link #start} returned it.
     * Does nothing when {@code detection} is {@code null}.
     */
    public void finish(final Detection detection) {
        if (detection != null) {
            record(detection.toViolation(detection.elapsedMillis(), Instant.now()));
        }
    }

    /**
     * Detect and record in one step, for operations there is nothing to time around.
     *
     * @throws ImpureTransactionException in {@link PuretxMode#FAIL}
     */
    public void report(final ViolationType type, final Supplier<String> summary) {
        final Detection detection = start(type, summary);
        if (detection != null) {
            record(detection.toViolation(Violation.UNKNOWN_DURATION, Instant.now()));
        }
    }

    /**
     * Reports a transaction that stayed open past {@code puretx.max-duration}.
     *
     * <p>The threshold, the comparison and the wording all belong here rather than in whichever
     * framework hook happens to notice the commit — that hook's job is to supply the elapsed time.
     *
     * @param quiet suppress the {@link PuretxMode#FAIL} exception. Set on the rollback path, where
     *              throwing would mask the failure that caused the rollback in the first place
     * @return whether anything was reported
     */
    public boolean reportLongTransaction(final long elapsedMillis, final boolean quiet) {
        final PuretxSettings s = settings;
        if (!isWatching(s, ViolationType.LONG_TRANSACTION) || !s.durationCheckEnabled()) {
            return false;
        }
        final long limit = s.maxDurationMillis();
        if (elapsedMillis < limit) {
            return false;
        }
        final Supplier<String> summary =
                () -> String.format(Locale.ROOT, "transaction held past the %,dms limit", limit);
        try {
            final Detection detection = start(ViolationType.LONG_TRANSACTION, summary);
            if (detection == null) {
                return false;
            }
            record(detection.toViolation(elapsedMillis, Instant.now()));
            return true;
        } catch (ImpureTransactionException ex) {
            if (quiet) {
                // Already recorded and logged by record(); the throw is the only part we drop.
                return true;
            }
            throw ex;
        }
    }

    private Violation record(final Violation violation) {
        store.add(violation);
        for (ViolationListener listener : listeners) {
            try {
                listener.onViolation(violation);
            } catch (RuntimeException ignored) {
                // A broken listener must not break the application it is observing.
            }
        }
        return violation;
    }
}
