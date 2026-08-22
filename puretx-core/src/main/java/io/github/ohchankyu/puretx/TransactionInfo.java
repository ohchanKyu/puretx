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
        String managerType) {

    /** Shown for a transaction started without a name, e.g. through TransactionTemplate. */
    private static final String UNNAMED = "<unnamed transaction>";

    /** Used when a transaction is known to be active but nothing else about it could be resolved. */
    public static final TransactionInfo UNKNOWN = new TransactionInfo("", -1, false, false, "");

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
