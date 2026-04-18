/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ResourceExhaustedException extends CloudException {

    public ResourceExhaustedException(String providerName, String operation, String message, Throwable cause) {
        super(providerName, operation, CloudErrorCodes.RESOURCE_EXHAUSTED, false, message, cause);
    }

    public ResourceExhaustedException(String providerName, String operation, String message) {
        this(providerName, operation, message, null);
    }
}
