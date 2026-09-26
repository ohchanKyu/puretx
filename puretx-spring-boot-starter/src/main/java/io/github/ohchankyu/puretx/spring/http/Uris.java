package io.github.ohchankyu.puretx.spring.http;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Renders a request URI for a report: scheme, host, port and path, and nothing else.
 *
 * <p>The query string is dropped, not masked. Access tokens, API keys and signed URLs still
 * travel as query parameters in plenty of APIs, and a violation report goes to the application
 * log, the violation store and the {@code FAIL} exception message — all places a secret must not
 * reach. User info before the host is dropped for the same reason, and the fragment because it
 * never reaches a server at all. The path is what identifies the call; the rest was never needed
 * to tell which call it was.
 */
final class Uris {

    private Uris() {}

    static String describe(final @Nullable URI uri) {
        if (uri == null) {
            return "";
        }
        if (uri.isOpaque() || uri.getScheme() == null) {
            return stripQuery(uri.toString());
        }
        final StringBuilder sb = new StringBuilder(64);
        sb.append(uri.getScheme()).append("://");
        if (uri.getHost() != null) {
            sb.append(uri.getHost());
            if (uri.getPort() >= 0) {
                sb.append(':').append(uri.getPort());
            }
        }
        final String path = uri.getRawPath();
        sb.append(path == null || path.isEmpty() ? "/" : path);
        return sb.toString();
    }

    private static String stripQuery(final String raw) {
        int end = raw.length();
        final int query = raw.indexOf('?');
        if (query >= 0) {
            end = query;
        }
        final int fragment = raw.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        return raw.substring(0, end);
    }
}
