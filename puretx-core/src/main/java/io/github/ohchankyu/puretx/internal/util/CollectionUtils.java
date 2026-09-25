package io.github.ohchankyu.puretx.internal.util;

import java.util.Collection;
import org.jspecify.annotations.Nullable;

public final class CollectionUtils {

    private CollectionUtils() {}

    public static boolean isEmpty(final @Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

}
