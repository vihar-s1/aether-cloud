/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class GenericCloudException extends CloudException {

    public GenericCloudException(
            String providerName, String operation, String errorCode, String message, Throwable cause) {
        this(providerName, operation, errorCode, false, message, cause);
    }

    public GenericCloudException(
            String providerName,
            String operation,
            String errorCode,
            boolean retryable,
            String message,
            Throwable cause) {
        super(providerName, operation, errorCode, retryable, message, cause);
    }
}
