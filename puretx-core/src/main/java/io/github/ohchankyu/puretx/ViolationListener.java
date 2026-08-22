package io.github.ohchankyu.puretx;

/**
 * Called for every violation puretx records.
 *
 * <p>Register your own to ship violations somewhere useful — a metric, a Slack webhook,
 * an assertion helper in tests. Listeners run on the thread that hit the violation, so keep
 * them cheap; anything they throw is swallowed.
 */
@FunctionalInterface
public interface ViolationListener {

    void onViolation(Violation violation);
}
