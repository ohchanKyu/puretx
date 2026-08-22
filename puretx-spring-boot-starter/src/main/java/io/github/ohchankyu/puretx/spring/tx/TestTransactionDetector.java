package io.github.ohchankyu.puretx.spring.tx;

/**
 * Recognises the transaction that Spring's TestContext framework opens around a {@code @Transactional}
 * test method and rolls back afterwards.
 *
 * <p>That transaction wraps the whole test — fixtures, the code under test, and the assertions —
 * so everything the test does looks like it happened in one long transaction. Reporting on it says
 * more about the test harness than about the production code, which is why puretx stays quiet there
 * unless {@code puretx.detect-in-test-transactions} says otherwise.
 */
final class TestTransactionDetector {

    private static final String LISTENER =
            "org.springframework.test.context.transaction.TransactionalTestExecutionListener";
    private static final String UTILS =
            "org.springframework.test.context.transaction.TestContextTransactionUtils";

    /** Cheap gate: in production spring-test is not on the classpath, so the walk never happens. */
    private static final boolean SPRING_TEST_PRESENT = isPresent(LISTENER);

    private static final int MAX_FRAMES = 64;

    private TestTransactionDetector() {
    }

    static boolean isTestManaged() {
        if (!SPRING_TEST_PRESENT) {
            return false;
        }
        return StackWalker.getInstance().walk(frames -> frames
                .limit(MAX_FRAMES)
                .anyMatch(frame -> {
                    String name = frame.getClassName();
                    return LISTENER.equals(name) || UTILS.equals(name);
                }));
    }

    private static boolean isPresent(final String className) {
        try {
            Class.forName(className, false, TestTransactionDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}
