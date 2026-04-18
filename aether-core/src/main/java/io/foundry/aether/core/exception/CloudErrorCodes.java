/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.exception;

public interface CloudErrorCodes {

    // resource
    String RESOURCE_NOT_FOUND = "aether.resource.not_found";
    String RESOURCE_EXHAUSTED = "aether.resource.exhausted";

    // storage
    String STORAGE_NOT_FOUND = "aether.storage.not_found";
    String STORAGE_ACCESS_DENIED = "aether.storage.access_denied";

    // compute
    String COMPUTE_NOT_FOUND = "aether.compute.not_found";

    // secrets
    String SECRET_NOT_FOUND = "aether.secrets.not_found";

    // auth
    String AUTH_FAILED = "aether.auth.failed";
    String AUTH_PERMISSION_DENIED = "aether.auth.permission_denied";

    // general
    String QUOTA_EXCEEDED = "aether.quota.exceeded";
    String PROVIDER_UNAVAILABLE = "aether.provider.unavailable";
    String OPERATION_NOT_SUPPORTED = "aether.operation.not_supported";
    String INVALID_CONFIG = "aether.config.invalid";
    String UNKNOWN = "aether.unknown";
}
