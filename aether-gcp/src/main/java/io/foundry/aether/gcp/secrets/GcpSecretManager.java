/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.secrets;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import io.foundry.aether.gcp.GcpCloudProvider;
import io.foundry.aether.gcp.internal.GcpUtils;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class GcpSecretManager implements SecretManager {

    private final GcpCloudProvider provider;
    private final SecretManagerServiceClient client;
    private final String projectId;

    public GcpSecretManager(GcpCloudProvider provider) {
        this.provider = provider;
        this.client = provider.secretManagerClient();
        this.projectId = provider.config().projectId();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        try {
            SecretVersionName versionName = SecretVersionName.of(projectId, secretId, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(versionName);
            String versionNum = _versionNum(response.getName());
            Secret secret = client.getSecret(SecretName.of(projectId, secretId));
            long createdAt = _toMillis(secret.getCreateTime());
            return new SecretValue(secretId, response.getPayload().getData().toStringUtf8(), versionNum, createdAt);
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "getSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        try {
            Secret secret = Secret.newBuilder()
                    .setReplication(
                            Replication.newBuilder().setAutomatic(Replication.Automatic.getDefaultInstance()).build())
                    .build();
            client.createSecret(ProjectName.of(projectId), secretId, secret);
            SecretVersion version = client.addSecretVersion(SecretName.of(projectId, secretId),
                    SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build());
            long now = System.currentTimeMillis();
            return new SecretMetadata(secretId, secretId, null, _versionNum(version.getName()), now, 0L);
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "createSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        try {
            Secret secret = client.getSecret(SecretName.of(projectId, secretId));
            long createdAt = _toMillis(secret.getCreateTime());
            SecretVersion version = client.addSecretVersion(SecretName.of(projectId, secretId),
                    SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build());
            return new SecretMetadata(secretId, secretId, null, _versionNum(version.getName()), createdAt,
                    System.currentTimeMillis());
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "updateSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public SecretValue rotate(String secretId) {
        try {
            SecretVersionName latestName = SecretVersionName.of(projectId, secretId, "latest");
            AccessSecretVersionResponse current = client.accessSecretVersion(latestName);
            String currentValue = current.getPayload().getData().toStringUtf8();
            SecretVersion newVersion = client.addSecretVersion(SecretName.of(projectId, secretId),
                    SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(currentValue)).build());
            return new SecretValue(secretId, currentValue, _versionNum(newVersion.getName()),
                    _toMillis(newVersion.getCreateTime()));
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "rotate", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public void deleteSecret(String secretId) {
        try {
            client.deleteSecret(SecretName.of(projectId, secretId));
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "deleteSecret", SECRET, secretId, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        try {
            List<SecretMetadata> result = new java.util.ArrayList<>();
            for (Secret secret : client.listSecrets(ProjectName.of(projectId)).iterateAll()) {
                String id = _secretId(secret.getName());
                long createdAt = _toMillis(secret.getCreateTime());
                result.add(new SecretMetadata(id, id, null, null, createdAt, 0L));
            }
            return result;
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "listSecrets", SECRET, null, CloudErrorCodes.SECRET_NOT_FOUND);
        }
    }

    private String _versionNum(String resourceName) {
        String[] parts = resourceName.split("/");
        return parts[parts.length - 1];
    }

    private String _secretId(String resourceName) {
        String[] parts = resourceName.split("/");
        return parts[parts.length - 1];
    }

    private long _toMillis(com.google.protobuf.Timestamp ts) {
        return ts != null ? ts.getSeconds() * 1000L + ts.getNanos() / 1_000_000 : 0L;
    }
}
