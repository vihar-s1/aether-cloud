/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.internal;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.PermissionDeniedException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.QuotaExceededException;
import io.foundry.aether.core.exception.ResourceAlreadyExistsException;
import io.foundry.aether.core.internal.ExceptionUtils;

public final class AzureUtils {

    private AzureUtils() {
    }

    public static CloudException wrapAzureException(Exception e, String operation, String resourceType,
            String resourceId) {
        if (e instanceof ResourceNotFoundException) {
            return new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                    operation, resourceType, resourceId, e);
        }
        if (e instanceof HttpResponseException httpEx) {
            int status = httpEx.getResponse().getStatusCode();
            return switch (status) {
                case 401 ->
                    new AuthenticationException(AzureCloudProvider.PROVIDER_NAME, operation, resourceType, httpEx);
                case 403 ->
                    new PermissionDeniedException(AzureCloudProvider.PROVIDER_NAME, operation, resourceType, httpEx);
                case 404 -> new io.foundry.aether.core.exception.ResourceNotFoundException(
                        AzureCloudProvider.PROVIDER_NAME, operation, resourceType, resourceId, httpEx);
                case 409 -> new ResourceAlreadyExistsException(AzureCloudProvider.PROVIDER_NAME, operation,
                        resourceType, resourceId, httpEx);
                case 429 -> new QuotaExceededException(AzureCloudProvider.PROVIDER_NAME, operation,
                        CloudErrorCodes.QUOTA_EXCEEDED, "Rate limit exceeded", httpEx);
                case 503 -> new ProviderUnavailableException(AzureCloudProvider.PROVIDER_NAME, operation,
                        "Azure service unavailable", httpEx);
                default -> new GenericCloudException(AzureCloudProvider.PROVIDER_NAME, operation,
                        CloudErrorCodes.UNKNOWN, httpEx.getMessage(), httpEx);
            };
        }
        return new GenericCloudException(AzureCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                ExceptionUtils.getRootCause(e), e);
    }
}
