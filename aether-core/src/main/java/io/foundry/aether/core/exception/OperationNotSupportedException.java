/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class OperationNotSupportedException extends CloudException {

    public OperationNotSupportedException(String providerName, String operation, String message, Throwable cause) {
        super(providerName, operation, CloudErrorCodes.OPERATION_NOT_SUPPORTED, false, message, cause);
    }

    public OperationNotSupportedException(String providerName, String operation, String message) {
        this(providerName, operation, message, null);
    }
}
