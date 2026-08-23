package io.github.ohchankyu.puretx;

import io.github.ohchankyu.puretx.internal.util.StringUtils;

/**
 * An immutable snapshot of the transaction that was open when a violation happened.
 *
 * @param name          the transaction name, normally {@code com.acme.OrderService.createOrder};
 *                      empty when the transaction was started without a name
 * @param elapsedMillis how long the transaction had already been open, or {@code -1} if unknown
 * @param readOnly      whether the transaction was declared read-only
 * @param testManaged   whether the transaction was opened by the Spring TestContext framework
 *                      (the rollback-after-test kind, not one your production code would open)
 * @param managerType   simple class name of the transaction manager, or empty if unknown
 */
public record TransactionInfo(
    String name,
    long elapsedMillis,
    boolean readOnly,
    boolean testManaged,
    String managerType,
    Object source
) {
    /**
     * Whatever the probe wants handed back to it later, or {@code null}.
     *
     * <p>Opaque on purpose: core stores it and returns it, and never looks inside. It exists so a
     * framework can recognise its own transaction again when a violation is recorded on a
     * different thread from the one that started it — which is what a reactive client does.
     */
    public Object source() {
        return source;
    }

    /** Shown for a transaction started without a name, e.g. through TransactionTemplate. */
    private static final String UNNAMED = "<unnamed transaction>";

    /** Used when a transaction is known to be active but nothing else about it could be resolved. */
    public static final TransactionInfo UNKNOWN = new TransactionInfo("", -1, false, false, "");

    /**
     * The same snapshot with {@link #source()} dropped.
     *
     * <p>The source is a live framework object — for Spring, the transaction status, and through it
     * the connection holder and the persistence context. Fine to pass to a listener that acts and
     * returns; not fine to keep. Anything that outlives the transaction stores this instead.
     */
    public TransactionInfo withoutSource() {
        return source == null ? this
                : new TransactionInfo(name, elapsedMillis, readOnly, testManaged, managerType, null);
    }

    /** For a probe with nothing to hand back. */
    public TransactionInfo(final String name, final long elapsedMillis, final boolean readOnly,
            final boolean testManaged, final String managerType) {
        this(name, elapsedMillis, readOnly, testManaged, managerType, null);
    }

    public TransactionInfo {
        name = StringUtils.defaultString(name);
        managerType = StringUtils.defaultString(managerType);
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    public boolean hasElapsed() {
        return elapsedMillis >= 0;
    }

    /**
     * The class that declared the transactional method: {@code com.acme.OrderService} out of
     * {@code com.acme.OrderService.createOrder}.
     *
     * <p>An ignore pattern is written against a class, and a transaction name is one segment
     * longer than one. Stripping the method here is what lets a pattern be matched as written,
     * instead of every pattern having to be loosened to tolerate the extra segment.
     */
    public String declaringTypeName() {
        final int method = name.lastIndexOf(StringUtils.DOT);
        return method <= 0 ? name : name.substring(0, method);
    }

    /** {@code com.acme.OrderService.createOrder} rendered as {@code OrderService.createOrder}. */
    public String displayName() {
        if (StringUtils.isEmpty(name)) {
            return UNNAMED;
        }
        final int method = name.lastIndexOf(StringUtils.DOT);
        if (method <= 0) {
            return name;
        }
        final int type = name.lastIndexOf(StringUtils.DOT, method - 1);
        return type < 0 ? name : name.substring(type + 1);
    }
}
