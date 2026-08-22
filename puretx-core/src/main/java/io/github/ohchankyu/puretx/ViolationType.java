package io.github.ohchankyu.puretx;

/** The kinds of impurity puretx knows how to spot. */
public enum ViolationType {

    /** An outbound HTTP call made while a transaction was open. */
    HTTP_CALL(
            "http",
            "HTTP call",
            """
            move the call outside the transaction, or defer it past the commit with
            @TransactionalEventListener(phase = AFTER_COMMIT)""",
            false),

    /** A message published to a broker while a transaction was open. */
    MESSAGE_PUBLISH(
            "messaging",
            "message publish",
            """
            publish after the transaction commits — @TransactionalEventListener(phase = AFTER_COMMIT)
            for the simple case, an outbox table when the message must not be lost""",
            false),

    /** A transaction that stayed open longer than the configured threshold. */
    LONG_TRANSACTION(
            "duration",
            "long-running transaction",
            """
            split the unit of work, or move the slow part outside the transaction —
            a connection and its locks are held for the whole duration""",
            true);

    private final String configKey;
    private final String displayName;
    private final String hint;
    private final boolean transactionItself;

    ViolationType(final String configKey, final String displayName, final String hint,
            final boolean transactionItself) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.hint = hint;
        this.transactionItself = transactionItself;
    }

    /** Matches the {@code puretx.detectors.*} property, so the startup log names what to switch off. */
    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    /** A short, actionable suggestion printed with every violation. */
    public String hint() {
        return hint;
    }

    /**
     * Whether the transaction <em>is</em> the violation, rather than something that happened inside it.
     *
     * <p>One fact, three consequences: there is no call site to resolve (it is detected at commit
     * time, where the stack says nothing useful), the report reads "open for" instead of
     * "started … ago", and the operation's own duration would only repeat the transaction's.
     */
    public boolean isTransactionItself() {
        return transactionItself;
    }

    /** Whether it is worth walking the stack for the application frame that caused this. */
    public boolean reportsCallSite() {
        return !transactionItself;
    }
}
