/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ResourceNotFoundException extends CloudException {

    private final String resourceId;

    public ResourceNotFoundException(String providerName, String operation, String resourceType, String resourceId,
            Throwable cause, String errorCode) {
        super(providerName, operation, errorCode, false,
                "Resource " + resourceType + " Not found for id: " + resourceId, cause);
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String providerName, String operation, String resourceType, String resourceId,
            Throwable cause) {
        this(providerName, operation, resourceType, resourceId, cause, CloudErrorCodes.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(String providerName, String operation, String resourceType, String resourceId) {
        this(providerName, operation, resourceType, resourceId, null);
    }

    public String resourceId() {
        return resourceId;
    }
}
