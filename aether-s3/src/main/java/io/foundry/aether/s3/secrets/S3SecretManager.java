/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.s3.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import io.foundry.aether.s3.S3CloudProvider;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;

public class S3SecretManager implements SecretManager {

    private final S3CloudProvider provider;
    private final SecretsManagerClient secretsClient;

    public S3SecretManager(S3CloudProvider provider) {
        this.provider = provider;
        secretsClient = SecretsManagerClient.builder()
                .region(Region.of(provider.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey(), provider.secretKey())))
                .build();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            return new SecretValue(
                    secretId,
                    _decryptSecretString(response.secretBinary()),
                    response.versionId(),
                    response.createdDate().toEpochMilli());
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(provider.name(), "getSecret", SECRET, secretId);
        }
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        CreateSecretResponse response = secretsClient.createSecret(CreateSecretRequest.builder()
                .name(secretId)
                .secretBinary(_encryptSecretString(value))
                .build());
        return new SecretMetadata(secretId, secretId, null, response.versionId(), System.currentTimeMillis(), 0L);
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        try {
            GetSecretValueResponse existing = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            PutSecretValueResponse response = secretsClient.putSecretValue(PutSecretValueRequest.builder()
                    .secretId(secretId)
                    .secretBinary(_encryptSecretString(value))
                    .build());
            return new SecretMetadata(
                    secretId,
                    existing.name(),
                    null,
                    response.versionId(),
                    existing.createdDate().toEpochMilli(),
                    System.currentTimeMillis());
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(provider.name(), "updateSecret", SECRET, secretId);
        }
    }

    @Override
    public SecretValue rotate(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            PutSecretValueResponse rotateResponse = secretsClient.putSecretValue(PutSecretValueRequest.builder()
                    .secretId(secretId)
                    .secretBinary(response.secretBinary())
                    .build());
            return new SecretValue(
                    secretId,
                    _decryptSecretString(response.secretBinary()),
                    rotateResponse.versionId(),
                    response.createdDate().toEpochMilli());
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(provider.name(), "rotate", SECRET, secretId);
        }
    }

    @Override
    public void deleteSecret(String secretId) {
        try {
            secretsClient.deleteSecret(DeleteSecretRequest.builder()
                    .secretId(secretId)
                    .forceDeleteWithoutRecovery(true)
                    .build());
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            throw new ResourceNotFoundException(provider.name(), "deleteSecret", SECRET, secretId);
        }
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        ListSecretsResponse response =
                secretsClient.listSecrets(ListSecretsRequest.builder().build());
        return response.secretList().stream().map(this::_entryToMetadata).toList();
    }

    private String _decryptSecretString(SdkBytes secretBinary) {
        return secretBinary.asUtf8String();
    }

    private SdkBytes _encryptSecretString(String secretString) {
        return SdkBytes.fromUtf8String(secretString);
    }

    private SecretMetadata _entryToMetadata(SecretListEntry entry) {
        long createdAt = entry.createdDate() != null ? entry.createdDate().toEpochMilli() : 0L;
        long lastRotated =
                entry.lastRotatedDate() != null ? entry.lastRotatedDate().toEpochMilli() : 0L;
        return new SecretMetadata(entry.name(), entry.name(), entry.description(), null, createdAt, lastRotated);
    }
}
