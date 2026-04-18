/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class PermissionDeniedException extends CloudException {

    public PermissionDeniedException(String providerName, String operation, String resourceType, Throwable cause) {
        super(providerName, operation, CloudErrorCodes.AUTH_PERMISSION_DENIED, false,
                "Permission denied for resource: " + resourceType, cause);
    }

    public PermissionDeniedException(String providerName, String operation, String resourceType) {
        this(providerName, operation, resourceType, null);
    }
}
