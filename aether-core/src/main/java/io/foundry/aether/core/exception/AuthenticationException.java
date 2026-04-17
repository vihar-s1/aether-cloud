/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public final class AuthenticationException extends CloudException {

    public AuthenticationException(String providerName, String operation, String resourceType, Throwable cause) {
        super(providerName, operation, null, false, "Authentication failed for resource: " + resourceType, cause);
    }

    public AuthenticationException(String providerName, String operation, String resourceType) {
        this(providerName, operation, resourceType, null);
    }
}
