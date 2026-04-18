/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class InvalidConfigurationException extends CloudException {

    public InvalidConfigurationException(String providerName, String operation, String message, Throwable cause,
            String errorCode) {
        super(providerName, operation, errorCode, false, message, cause);
    }

    public InvalidConfigurationException(String providerName, String operation, String message, Throwable cause) {
        this(providerName, operation, message, cause, CloudErrorCodes.INVALID_CONFIG);
    }

    public InvalidConfigurationException(String providerName, String operation, String message) {
        this(providerName, operation, message, null);
    }
}
