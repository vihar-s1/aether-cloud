/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ProviderUnavailableException extends CloudException {

    public ProviderUnavailableException(String providerName, String operation, String message, Throwable cause,
            String errorCode) {
        super(providerName, operation, errorCode, true, message, cause);
    }

    public ProviderUnavailableException(String providerName, String operation, String message, Throwable cause) {
        this(providerName, operation, message, cause, CloudErrorCodes.PROVIDER_UNAVAILABLE);
    }

    public ProviderUnavailableException(String providerName, String operation, String message) {
        this(providerName, operation, message, null);
    }
}
