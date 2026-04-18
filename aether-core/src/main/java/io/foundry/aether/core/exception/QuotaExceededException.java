/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class QuotaExceededException extends CloudException {

    public QuotaExceededException(String providerName, String operation, String errorCode, String message,
            Throwable cause) {
        super(providerName, operation, errorCode, true, message, cause);
    }

    public QuotaExceededException(String providerName, String operation, String message) {
        this(providerName, operation, CloudErrorCodes.QUOTA_EXCEEDED, message, null);
    }
}
