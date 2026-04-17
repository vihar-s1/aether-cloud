/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.internal;

public final class StringUtils {

    private StringUtils() {}

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static String concat(String... strings) {
        return String.join("", strings);
    }
}
