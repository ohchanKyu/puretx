package io.github.ohchankyu.puretx.internal;

import io.github.ohchankyu.puretx.internal.util.CollectionUtils;
import io.github.ohchankyu.puretx.internal.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Ant-flavoured matching for class names: {@code com.acme.legacy.**}, {@code com.acme.*.OrderService},
 * or a plain fully-qualified name.
 *
 * <p>{@code *} stops at a package separator, {@code **} does not.
 */
public final class PackagePatterns {

    /** {@code **} — crosses package separators, so {@code com.acme.**} reaches any depth. */
    private static final String ANY_DEPTH = ".*";

    /** {@code *} — stops at a package separator, so {@code com.acme.*.Order} is one level only. */
    private static final String SINGLE_SEGMENT = "[^.]*";

    /** {@code ?} — exactly one character. */
    private static final String ANY_CHARACTER = ".";

    /**
     * Appended to every pattern so it also covers everything below what it names.
     *
     * <p>Load-bearing for transaction names: Spring calls a transaction
     * {@code com.acme.OrderService.createOrder}, one segment longer than the class an ignore
     * pattern is written against. Without this, a pattern would match the call site and not the
     * transaction — and only for some patterns, which is worse than not working at all.
     */
    private static final String SUBPACKAGE_SUFFIX = "(\\..*)?";

    /** Regex fragments are longer than the characters they replace; this avoids a resize or two. */
    private static final int REGEX_GROWTH_HEADROOM = 16;

    /** Matches nothing. */
    public static final PackagePatterns NONE = new PackagePatterns(List.of());

    private final List<Pattern> patterns;

    private PackagePatterns(final List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public static PackagePatterns of(final List<String> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            return NONE;
        }
        final List<Pattern> compiled = new ArrayList<>(raw.size());
        for (final String pattern : raw) {
            if (StringUtils.isNotBlank(pattern)) {
                compiled.add(Pattern.compile(toRegex(pattern.trim())));
            }
        }
        return compiled.isEmpty() ? NONE : new PackagePatterns(compiled);
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public boolean matches(final String className) {
        if (patterns.isEmpty() || StringUtils.isEmpty(className)) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(className).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String toRegex(final String pattern) {
        // A pattern with no wildcards is read the way people mean it: "com.acme.legacy" covers
        // everything under that package, and "com.acme.OrderService" covers that one class.
        if (pattern.indexOf(StringUtils.ASTERISK) < 0 && pattern.indexOf(StringUtils.QUESTION_MARK) < 0) {
            return Pattern.quote(pattern) + SUBPACKAGE_SUFFIX;
        }
        final StringBuilder sb = new StringBuilder(pattern.length() + REGEX_GROWTH_HEADROOM);
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            if (c == StringUtils.ASTERISK) {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == StringUtils.ASTERISK) {
                    sb.append(ANY_DEPTH);
                    i += 2;
                    continue;
                }
                sb.append(SINGLE_SEGMENT);
                i++;
                continue;
            }
            if (c == StringUtils.QUESTION_MARK) {
                sb.append(ANY_CHARACTER);
                i++;
                continue;
            }
            sb.append(Pattern.quote(String.valueOf(c)));
            i++;
        }
        // The wildcard-free branch above already ends this way. Leaving it off here is what made
        // "com.acme.*.OrderService" miss transaction names while "com.acme.orders" matched them.
        return sb.append(SUBPACKAGE_SUFFIX).toString();
    }
}
