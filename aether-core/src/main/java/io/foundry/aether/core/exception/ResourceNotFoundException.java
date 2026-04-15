/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ResourceNotFoundException extends CloudException {

    private final String resourceId;

    public ResourceNotFoundException(
            String providerName, String operation, String resourceId, String message, Throwable cause) {
        super(providerName, operation, null, false, message, cause);
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String providerName, String operation, String resourceId, String message) {
        this(providerName, operation, resourceId, message, null);
    }

    public String resourceId() {
        return resourceId;
    }
}
