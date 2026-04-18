/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public abstract sealed class CloudException extends RuntimeException
        permits AuthenticationException, PermissionDeniedException, ResourceNotFoundException,
        ResourceExhaustedException, QuotaExceededException, ProviderUnavailableException, InvalidConfigurationException,
        OperationNotSupportedException, GenericCloudException {

    private final String providerName;
    private final String operation;
    private final String errorCode;
    private final boolean retryable;

    protected CloudException(String providerName, String operation, String errorCode, boolean retryable, String message,
            Throwable cause) {
        super("[" + providerName + "/" + operation + "] " + message, cause);
        this.providerName = providerName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String providerName() {
        return providerName;
    }

    public String operation() {
        return operation;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
