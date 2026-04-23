/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class ResourceAlreadyExistsException extends CloudException {

    private final String resourceId;

    public ResourceAlreadyExistsException(
            String providerName, String operation, String resourceType, String resourceId, Throwable cause) {
        super(providerName, operation, CloudErrorCodes.RESOURCE_ALREADY_EXISTS, false,
                "Resource " + resourceType + " already exists for id: " + resourceId, cause);
        this.resourceId = resourceId;
    }

    public String resourceId() {
        return resourceId;
    }
}
