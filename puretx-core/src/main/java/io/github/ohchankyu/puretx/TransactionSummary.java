package io.github.ohchankyu.puretx;

/**
 * What one transaction spent its life on, reported once when it ends.
 *
 * <p>The per-call reports say a transaction was interrupted. This says how much of it was the
 * interruption — and that is the sentence that moves people. "You made an HTTP call inside a
 * transaction" invites a shrug; "this transaction was open for 1,640ms and spent 93% of it waiting
 * on two remote systems" does not.
 *
 * @param transactionName the transaction, {@code com.acme.OrderService.placeOrder}
 * @param transactionMillis how long it stayed open
 * @param callCount how many reported calls happened inside it
 * @param callMillis how long those calls took in total
 */
public record TransactionSummary(
    String transactionName,
    long transactionMillis,
    int callCount,
    long callMillis
) {
    /** The share of the transaction's life spent on the calls, 0-100. */
    public int percentageSpentOnCalls() {
        if (transactionMillis <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(callMillis * 100.0 / transactionMillis));
    }

    /** {@code com.acme.OrderService.placeOrder} rendered as {@code OrderService.placeOrder}. */
    public String displayName() {
        return new TransactionInfo(transactionName, transactionMillis, false, false, "").displayName();
    }
}
