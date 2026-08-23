package io.github.ohchankyu.puretx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default listener: writes the violation block at WARN level under the {@code io.github.ohchankyu.puretx}
 * logger, so it can be turned down without touching configuration elsewhere.
 */
public final class LoggingViolationListener implements ViolationListener {

    private static final Logger log = LoggerFactory.getLogger(Puretx.LOGGER_NAME);

    @Override
    public void onTransactionSummary(final TransactionSummary summary) {
        if (log.isWarnEnabled()) {
            log.warn("{}", ViolationFormatter.format(summary));
        }
    }

    @Override
    public void onViolation(final Violation violation) {
        if (log.isWarnEnabled()) {
            log.warn("{}", ViolationFormatter.formatWithCallPath(violation));
        }
    }
}
