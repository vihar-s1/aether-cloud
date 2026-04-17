/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.s3.secrets;

import io.foundry.aether.core.secrets.AbstractSecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.s3.S3CloudProvider;
import java.util.Collection;
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

public class S3SecretManager extends AbstractSecretManager<S3CloudProvider> {

    private final SecretsManagerClient secretsClient;

    public S3SecretManager(S3CloudProvider provider) {
        super(provider);
        secretsClient = SecretsManagerClient.builder()
                .region(Region.of(provider.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey(), provider.secretKey())))
                .build();
    }

    @Override
    protected SecretEntry readEntry(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            SecretMetadata metadata = new SecretMetadata(
                    secretId,
                    response.name(),
                    null,
                    response.versionId(),
                    response.createdDate().toEpochMilli(),
                    0L);
            return SecretEntry.of(_decryptSecretString(response.secretBinary()), metadata);
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            return null;
        }
    }

    @Override
    protected SecretMetadata findSecretMetadata(String secretId) {
        try {
            GetSecretValueResponse response = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());
            return new SecretMetadata(
                    secretId,
                    response.name(),
                    null,
                    response.versionId(),
                    response.createdDate().toEpochMilli(),
                    0L);
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            return null;
        }
    }

    @Override
    protected Collection<SecretMetadata> listEntries() {
        ListSecretsResponse response =
                secretsClient.listSecrets(ListSecretsRequest.builder().build());
        return response.secretList().stream().map(this::_secretEntryToMetadata).toList();
    }

    @Override
    protected VersionInfo createEntry(String secretId, String value) {
        CreateSecretResponse response = secretsClient.createSecret(CreateSecretRequest.builder()
                .name(secretId)
                .secretBinary(_encryptSecretString(value))
                .build());
        return new VersionInfo(response.versionId(), System.currentTimeMillis());
    }

    @Override
    protected VersionInfo updateEntry(String secretId, String value) {
        PutSecretValueResponse response = secretsClient.putSecretValue(PutSecretValueRequest.builder()
                .secretId(secretId)
                .secretBinary(_encryptSecretString(value))
                .build());
        return new VersionInfo(response.versionId(), System.currentTimeMillis());
    }

    @Override
    protected SecretEntry deleteEntry(String secretId) {
        SecretEntry existing = readEntry(secretId);
        if (existing == null) {
            return null;
        }
        try {
            secretsClient.deleteSecret(DeleteSecretRequest.builder()
                    .secretId(secretId)
                    .forceDeleteWithoutRecovery(true)
                    .build());
        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            return null;
        }
        return existing;
    }

    private String _decryptSecretString(SdkBytes secretBinary) {
        return secretBinary.asUtf8String();
    }

    private SdkBytes _encryptSecretString(String secretString) {
        return SdkBytes.fromUtf8String(secretString);
    }

    private SecretMetadata _secretEntryToMetadata(SecretListEntry entry) {
        long createdAt = entry.createdDate() != null ? entry.createdDate().toEpochMilli() : 0L;
        long lastRotated =
                entry.lastRotatedDate() != null ? entry.lastRotatedDate().toEpochMilli() : 0L;
        return new SecretMetadata(entry.name(), entry.name(), entry.description(), null, createdAt, lastRotated);
    }
}
