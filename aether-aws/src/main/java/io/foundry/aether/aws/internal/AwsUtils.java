/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.internal;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.PermissionDeniedException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.QuotaExceededException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.ExceptionUtils;
import io.foundry.aether.core.internal.StringUtils;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

public final class AwsUtils {

    private AwsUtils() {
    }

    public static <B extends AwsClientBuilder<B, ?>> B applyCommonConfig(B builder, AwsCloudProvider provider) {
        builder.region(Region.of(provider.region())).credentialsProvider(StaticCredentialsProvider
                .create(AwsBasicCredentials.create(provider.accessKey(), provider.secretKey())));
        if (!StringUtils.isEmpty(provider.endpoint())) {
            builder.endpointOverride(URI.create(provider.endpoint()));
        }
        return builder;
    }

    public static CloudException wrapAwsException(Exception e, String operation, String resourceType, String resourceId,
            String defaultErrorCode) {
        if (e instanceof NoSuchKeyException) {
            return new ResourceNotFoundException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, resourceId, e,
                    AwsErrorCodes.S3_NO_SUCH_KEY);
        }
        if (e instanceof NoSuchBucketException) {
            return new ResourceNotFoundException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, resourceId, e,
                    AwsErrorCodes.S3_NO_SUCH_BUCKET);
        }
        if (e instanceof software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException) {
            return new ResourceNotFoundException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, resourceId, e,
                    defaultErrorCode);
        }
        if (e instanceof AwsServiceException awsEx) {
            String errorCode = awsEx.awsErrorDetails() != null ? awsEx.awsErrorDetails().errorCode() : "";
            if ("InvalidInstanceID.NotFound".equals(errorCode) || "InvalidInstanceID.Malformed".equals(errorCode)) {
                return new ResourceNotFoundException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType,
                        resourceId, awsEx, defaultErrorCode);
            }
            return switch (awsEx.statusCode()) {
                case 401 -> new AuthenticationException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, awsEx);
                case 403 ->
                    new PermissionDeniedException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, awsEx);
                case 404 -> new ResourceNotFoundException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType,
                        resourceId, awsEx, defaultErrorCode);
                case 429 -> new QuotaExceededException(AwsCloudProvider.PROVIDER_NAME, operation,
                        CloudErrorCodes.QUOTA_EXCEEDED, "Rate limit exceeded", awsEx);
                case 503 -> new ProviderUnavailableException(AwsCloudProvider.PROVIDER_NAME, operation,
                        "AWS service unavailable", awsEx);
                default -> new GenericCloudException(AwsCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                        awsEx.getMessage(), awsEx);
            };
        }
        if (e instanceof SdkClientException sdkEx) {
            if (sdkEx.getMessage() != null && sdkEx.getMessage().contains("Signature does not match")) {
                return new AuthenticationException(AwsCloudProvider.PROVIDER_NAME, operation, resourceType, sdkEx,
                        AwsErrorCodes.AUTH_SIGNATURE_MISMATCH);
            }
            if (sdkEx.getMessage() != null && sdkEx.getMessage().contains("Unable to execute HTTP request")) {
                return new ProviderUnavailableException(AwsCloudProvider.PROVIDER_NAME, operation,
                        "AWS service unavailable", sdkEx);
            }
        }
        return new GenericCloudException(AwsCloudProvider.PROVIDER_NAME, operation, CloudErrorCodes.UNKNOWN,
                ExceptionUtils.getRootCause(e), e);
    }
}
