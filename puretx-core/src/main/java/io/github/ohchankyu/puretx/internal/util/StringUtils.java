package io.github.ohchankyu.puretx.internal.util;

/**
 * Small string helpers.
 *
 * <p>Spring has these, but {@code puretx-core} depending on zero frameworks is the reason that
 * module exists at all, so the handful it needs live here instead.
 */
public final class StringUtils {

    /** Matches any run of characters, including package separators: {@code com.acme.**}. */
    public static final char ASTERISK = '*';

    /** Matches exactly one character: {@code com.acme.Order?}. */
    public static final char QUESTION_MARK = '?';

    /** The package separator. */
    public static final char DOT = '.';

    /** The empty string, used where {@code null} needs a harmless stand-in. */
    public static final String EMPTY = "";

    private StringUtils() {
    }

    /** True when {@code str} is {@code null} or has no characters. {@code "  "} is <em>not</em> empty. */
    public static boolean isEmpty(final String str) {
        return str == null || str.isEmpty();
    }


    /** True when {@code str} is {@code null}, empty, or nothing but whitespace. */
    public static boolean isBlank(final String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(final String str) {
        return !isBlank(str);
    }

    /** {@code str}, or the empty string when it is {@code null}. */
    public static String defaultString(final String str) {
        return str == null ? EMPTY : str;
    }
}
