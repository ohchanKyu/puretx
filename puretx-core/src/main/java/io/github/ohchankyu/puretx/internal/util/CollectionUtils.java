package io.github.ohchankyu.puretx.internal.util;

import java.util.Collection;

/**
 * Small collection helpers.
 *
 * @see StringUtils
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    /** True when {@code collection} is {@code null} or has no elements. */
    public static boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

}
