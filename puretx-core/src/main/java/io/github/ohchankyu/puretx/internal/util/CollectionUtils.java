package io.github.ohchankyu.puretx.internal.util;

import java.util.Collection;

public final class CollectionUtils {

    private CollectionUtils() {}

    public static boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

}
