/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.s3.internal;

import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.ExceptionUtils;
import io.foundry.aether.s3.S3CloudProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

public final class S3Utils {

    private S3Utils() {}

    /**
     * Map AWS S3 SDK exceptions to Aether CloudExceptions
     */
    public static CloudException wrapS3Exception(
            Exception e, String operation, String resourceType, String resourceId) {
        if (e instanceof NoSuchBucketException || e instanceof NoSuchKeyException) {
            return new ResourceNotFoundException(S3CloudProvider.PROVIDER_NAME, operation, resourceType, resourceId);
        }
        if (e instanceof SdkClientException sdkEx) {
            if (sdkEx.getMessage() != null && sdkEx.getMessage().contains("Signature does not match")) {
                return new AuthenticationException(S3CloudProvider.PROVIDER_NAME, operation, resourceType);
            }
            if (sdkEx.getMessage() != null && sdkEx.getMessage().contains("Unable to execute HTTP request")) {
                return new ProviderUnavailableException(
                        S3CloudProvider.PROVIDER_NAME, operation, "S3 service unavailable", sdkEx);
            }
        }
        String rootCause = ExceptionUtils.getRootCause(e);
        return new GenericCloudException(S3CloudProvider.PROVIDER_NAME, operation, resourceType, rootCause, e);
    }

    /**
     * Map S3-compatible HTTP errors to CloudExceptions
     */
    public static CloudException wrapHttpException(
            int statusCode, String message, String operation, String resourceType, String resourceId) {
        return switch (statusCode) {
            case 401, 403 -> new AuthenticationException(S3CloudProvider.PROVIDER_NAME, operation, resourceType);
            case 404 -> new ResourceNotFoundException(
                    S3CloudProvider.PROVIDER_NAME, operation, resourceType, resourceId);
            case 429, 503 -> new ProviderUnavailableException(
                    S3CloudProvider.PROVIDER_NAME, operation, "Rate limited or service unavailable", null);
            default -> new GenericCloudException(S3CloudProvider.PROVIDER_NAME, operation, resourceType, message, null);
        };
    }
}
