/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.internal;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.storage.StorageException;
import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.PermissionDeniedException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.QuotaExceededException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.ExceptionUtils;
import io.foundry.aether.gcp.GcpCloudProvider;

public final class GcpUtils {

    private GcpUtils() {
    }

    public static CloudException wrapGcpException(Exception e, String operation, String resourceType, String resourceId,
            String defaultErrorCode) {
        if (e instanceof StorageException storageEx) {
            return switch (storageEx.getCode()) {
                case 401 ->
                    new AuthenticationException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType, storageEx);
                case 403 ->
                    new PermissionDeniedException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType, storageEx);
                case 404 -> new ResourceNotFoundException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType,
                        resourceId, storageEx, defaultErrorCode);
                case 429 -> new QuotaExceededException(GcpCloudProvider.PROVIDER_NAME, operation,
                        CloudErrorCodes.QUOTA_EXCEEDED, "Rate limit exceeded", storageEx);
                case 503 -> new ProviderUnavailableException(GcpCloudProvider.PROVIDER_NAME, operation,
                        "GCP service unavailable", storageEx);
                default -> new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                        storageEx.getMessage(), storageEx);
            };
        }
        if (e instanceof ApiException apiEx) {
            return switch (apiEx.getStatusCode().getCode()) {
                case NOT_FOUND -> new ResourceNotFoundException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType,
                        resourceId, apiEx, defaultErrorCode);
                case PERMISSION_DENIED ->
                    new PermissionDeniedException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType, apiEx);
                case UNAUTHENTICATED ->
                    new AuthenticationException(GcpCloudProvider.PROVIDER_NAME, operation, resourceType, apiEx);
                case RESOURCE_EXHAUSTED -> new QuotaExceededException(GcpCloudProvider.PROVIDER_NAME, operation,
                        CloudErrorCodes.QUOTA_EXCEEDED, "GCP quota exceeded", apiEx);
                case UNAVAILABLE -> new ProviderUnavailableException(GcpCloudProvider.PROVIDER_NAME, operation,
                        "GCP service unavailable", apiEx);
                default -> new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                        apiEx.getMessage(), apiEx);
            };
        }
        return new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                ExceptionUtils.getRootCause(e), e);
    }
}
