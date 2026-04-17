/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.internal;

public final class ExceptionUtils {

    private ExceptionUtils() {}

    public static String getRootCause(Throwable th) {
        Throwable root = th;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.toString();
    }
}
