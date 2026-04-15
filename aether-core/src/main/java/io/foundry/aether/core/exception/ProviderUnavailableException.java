/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ProviderUnavailableException extends CloudException {

    public ProviderUnavailableException(String providerName, String operation, String message, Throwable cause) {
        super(providerName, operation, null, true, message, cause);
    }

    public ProviderUnavailableException(String providerName, String operation, String message) {
        this(providerName, operation, message, null);
    }
}
