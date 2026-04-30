/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.secrets;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.ResourceAlreadyExistsException;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AwsSecretsManager implements SecretManager {

    private final AwsCloudProvider provider;
    private final SecretsManagerClient secretsClient;

    public AwsSecretsManager(AwsCloudProvider provider) {
        this.provider = provider;
        this.secretsClient = provider.secretsManagerClient();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient
                    .getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
            return new SecretValue(secretId, _extractSecretValue(response), response.versionId(),
                    response.createdDate() != null ? response.createdDate().toEpochMilli() : 0L);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "getSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        try {
            CreateSecretResponse response = secretsClient
                    .createSecret(CreateSecretRequest.builder().name(secretId).secretString(value).build());
            return new SecretMetadata(secretId, secretId, null, response.versionId(), System.currentTimeMillis(), 0L);
        } catch (ResourceExistsException e) {
            throw new ResourceAlreadyExistsException(provider.name(), "createSecret", SECRET, secretId, e);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "createSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        try {
            PutSecretValueResponse response = secretsClient
                    .putSecretValue(PutSecretValueRequest.builder().secretId(secretId).secretString(value).build());
            return new SecretMetadata(secretId, secretId, null, response.versionId(), 0L, System.currentTimeMillis());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "updateSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretValue rotate(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient
                    .getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
            String currentValue = _extractSecretValue(response);
            PutSecretValueResponse rotateResponse = secretsClient.putSecretValue(
                    PutSecretValueRequest.builder().secretId(secretId).secretString(currentValue).build());
            return new SecretValue(secretId, currentValue, rotateResponse.versionId(),
                    response.createdDate() != null ? response.createdDate().toEpochMilli() : 0L);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "rotate", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public void deleteSecret(String secretId) {
        try {
            secretsClient.deleteSecret(
                    DeleteSecretRequest.builder().secretId(secretId).forceDeleteWithoutRecovery(true).build());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "deleteSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public ListResponse<SecretMetadata> listSecrets(ListRequest<SecretMetadata> request) {
        try {
            var reqBuilder = software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest.builder()
                    .nextToken(request.cursor()).maxResults(request.limit());
            var awsRequest = reqBuilder.build();
            var response = secretsClient.listSecrets(awsRequest);
            List<SecretMetadata> secrets = response.secretList().stream().map(this::_entryToMetadata).toList();
            String nextCursor = response.nextToken();
            return new ListResponse<>(secrets, nextCursor, nextCursor != null);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "listSecrets", SECRET, null, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    private String _extractSecretValue(GetSecretValueResponse response) {
        if (response.secretString() != null) {
            return response.secretString();
        }
        return response.secretBinary() != null ? response.secretBinary().asUtf8String() : null;
    }

    private SecretMetadata _entryToMetadata(SecretListEntry entry) {
        long createdAt = entry.createdDate() != null ? entry.createdDate().toEpochMilli() : 0L;
        long lastRotated = entry.lastRotatedDate() != null ? entry.lastRotatedDate().toEpochMilli() : 0L;
        return new SecretMetadata(entry.name(), entry.name(), entry.description(), null, createdAt, lastRotated);
    }
}
