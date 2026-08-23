package io.github.ohchankyu.puretx.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the application frame responsible for a violation, and optionally keeps the stack around it.
 */
public final class StackCapture {

    private static final int MAX_SCAN = 96;

    private static final String PURETX_PREFIX = "io.github.ohchankyu.puretx.";

    /** Frames that are never the answer to "where in my code did this come from?". */
    private static final String[] INFRASTRUCTURE = {
        PURETX_PREFIX,
        "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
        "org.springframework.",
        "org.apache.", "org.eclipse.", "org.jboss.",
        "okhttp3.", "okio.", "io.netty.", "reactor.", "feign.",
        "kotlin.", "kotlinx.",
        "org.junit.", "junit.", "org.mockito.", "net.bytebuddy.", "org.objenesis.",
        "org.slf4j.", "ch.qos.logback.", "org.apache.logging.",
        "io.micrometer.", "org.hibernate.", "com.zaxxer.", "com.fasterxml.",
        "org.gradle.", "worker.org.gradle.",
    };

    private StackCapture() {}

    public record Result(StackTraceElement origin, List<StackTraceElement> callPath) {
        public static final Result EMPTY = new Result(null, List.of());
    }

    /**
     * @param appPackages when non-empty, the only prefixes considered "application code";
     *                    otherwise anything that is not obviously infrastructure counts
     * @param captureCallPath whether to keep the chain of application frames that led here
     * @param depth how many frames to keep when {@code captureCallPath} is set
     */
    public static Result capture(
        final PackagePatterns appPackages,
        final boolean captureCallPath,
        final int depth
    ) {
        return StackWalker.getInstance().walk(frames -> collect(frames, appPackages, captureCallPath, depth));
    }

    /**
     * Runs inside the walk so that class names are read straight off the frames.
     *
     * <p>Converting a frame to a {@link StackTraceElement} resolves its file name and line number,
     * which is the expensive part; only the handful of frames actually kept are worth that.
     */
    private static Result collect(
        final Stream<StackWalker.StackFrame> frames,
        final PackagePatterns appPackages,
        final boolean captureCallPath,
        final int depth
    ) {
        StackWalker.StackFrame origin = null;
        final List<StackWalker.StackFrame> path = captureCallPath ? new ArrayList<>(depth) : null;
        final List<StackWalker.StackFrame> fallback = captureCallPath ? new ArrayList<>(depth) : null;

        for (final StackWalker.StackFrame frame : (Iterable<StackWalker.StackFrame>) frames.limit(MAX_SCAN)::iterator) {
            final String className = frame.getClassName();
            if (className.startsWith(PURETX_PREFIX) && !appPackages.matches(className)) {
                continue;
            }
            if (isGeneratedProxy(className)) {
                continue;
            }
            if (isApplicationFrame(className, appPackages)) {
                if (origin == null) {
                    origin = frame;
                }
                if (path == null) {
                    break;
                }
                if (path.size() < depth) {
                    path.add(frame);
                }
                if (path.size() == depth) {
                    break;
                }
            } else if (fallback != null && path.isEmpty() && fallback.size() < depth) {
                fallback.add(frame);
            }
        }

        final List<StackWalker.StackFrame> kept = path == null ? List.of() : (path.isEmpty() ? fallback : path);
        return new Result(origin == null ? null : origin.toStackTraceElement(), toElements(kept));
    }

    private static List<StackTraceElement> toElements(final List<StackWalker.StackFrame> frames) {
        if (frames.isEmpty()) {
            return List.of();
        }
        final List<StackTraceElement> elements = new ArrayList<>(frames.size());
        for (final StackWalker.StackFrame frame : frames) {
            elements.add(frame.toStackTraceElement());
        }
        return List.copyOf(elements);
    }

    /**
     * Generated proxies sit in the stack under the application's own package name, but they are
     * not a place anybody can go and edit. Reporting {@code OrderService$$SpringCGLIB$$0} as the
     * call site would send a reader looking for a file that does not exist.
     */
    private static boolean isGeneratedProxy(final String className) {
        if (className.indexOf('$') < 0) {
            return false;
        }
        return className.contains("$$SpringCGLIB$$")
                || className.contains("$$EnhancerBySpringCGLIB$$")
                || className.contains("$$FastClassBySpringCGLIB$$")
                || className.contains("$HibernateProxy")
                || className.startsWith("jdk.proxy")
                || className.startsWith("com.sun.proxy.");
    }

    private static boolean isApplicationFrame(final String className, final PackagePatterns appPackages) {
        if (!appPackages.isEmpty()) {
            return appPackages.matches(className);
        }
        for (String prefix : INFRASTRUCTURE) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}
