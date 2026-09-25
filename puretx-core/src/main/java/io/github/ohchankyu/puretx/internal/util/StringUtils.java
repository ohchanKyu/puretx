package io.github.ohchankyu.puretx.internal.util;

import org.jspecify.annotations.Nullable;

public final class StringUtils {

    public static final char ASTERISK = '*';

    public static final char QUESTION_MARK = '?';

    public static final char DOT = '.';

    public static final String EMPTY = "";

    private StringUtils() {}

    public static boolean isEmpty(final @Nullable String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isBlank(final @Nullable String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(final String str) {
        return !isBlank(str);
    }

    public static String defaultString(final @Nullable String str) {
        return str == null ? EMPTY : str;
    }
}
