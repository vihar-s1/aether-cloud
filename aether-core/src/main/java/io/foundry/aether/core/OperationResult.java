/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

import java.time.Duration;

public record OperationResult<T>(T value, boolean success, String message, Duration duration) {

    public static <T> OperationResult<T> success(T value, Duration duration) {
        return new OperationResult<>(value, true, null, duration);
    }

    public static <T> OperationResult<T> failure(String message, Duration duration) {
        return new OperationResult<>(null, false, message, duration);
    }
}
