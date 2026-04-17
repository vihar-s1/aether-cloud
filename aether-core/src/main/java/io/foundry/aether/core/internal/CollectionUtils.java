/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class CollectionUtils {

    private CollectionUtils() {}

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static <FROM, TO> List<TO> transformList(
            List<FROM> input, java.util.function.Function<FROM, TO> transformer) {
        if (input == null) {
            return List.of();
        }
        return input.stream().map(transformer).toList();
    }
}
