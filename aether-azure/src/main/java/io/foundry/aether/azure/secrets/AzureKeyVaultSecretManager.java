/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.secrets;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.internal.AzureUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.ResourceAlreadyExistsException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AzureKeyVaultSecretManager implements SecretManager {

    private final AzureCloudProvider provider;
    private final SecretClient secretClient;

    public AzureKeyVaultSecretManager(AzureCloudProvider provider) {
        this.provider = provider;
        this.secretClient = provider.secretClient();
    }

    AzureKeyVaultSecretManager(AzureCloudProvider provider, SecretClient secretClient) {
        this.provider = provider;
        this.secretClient = secretClient;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        try {
            KeyVaultSecret secret = secretClient.getSecret(secretId);
            SecretProperties props = secret.getProperties();
            long createdAt = props.getCreatedOn() != null ? props.getCreatedOn().toInstant().toEpochMilli() : 0L;
            return new SecretValue(secretId, secret.getValue(), props.getVersion(), createdAt);
        } catch (ResourceNotFoundException e) {
            throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                    "getSecret", SECRET, secretId, e);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "getSecret", SECRET, secretId);
        }
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        // Key Vault has no atomic create-only operation; check then create is a TOCTOU
        // but is the best available with this API
        try {
            secretClient.getSecret(secretId);
            throw new ResourceAlreadyExistsException(AzureCloudProvider.PROVIDER_NAME, "createSecret", SECRET, secretId,
                    null);
        } catch (ResourceNotFoundException ignored) {
            // expected — secret does not exist
        }
        try {
            KeyVaultSecret created = secretClient.setSecret(secretId, value);
            SecretProperties props = created.getProperties();
            long createdAt = props.getCreatedOn() != null
                    ? props.getCreatedOn().toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            return new SecretMetadata(secretId, secretId, null, props.getVersion(), createdAt, 0L);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "createSecret", SECRET, secretId);
        }
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        SecretValue existing = getSecret(secretId);
        try {
            KeyVaultSecret updated = secretClient.setSecret(secretId, value);
            SecretProperties props = updated.getProperties();
            return new SecretMetadata(secretId, secretId, null, props.getVersion(), existing.createdAtMs(),
                    System.currentTimeMillis());
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "updateSecret", SECRET, secretId);
        }
    }

    @Override
    public SecretValue rotate(String secretId) {
        SecretValue existing = getSecret(secretId);
        try {
            KeyVaultSecret rotated = secretClient.setSecret(secretId, existing.value());
            SecretProperties props = rotated.getProperties();
            return new SecretValue(secretId, existing.value(), props.getVersion(), existing.createdAtMs());
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "rotate", SECRET, secretId);
        }
    }

    @Override
    public void deleteSecret(String secretId) {
        try {
            secretClient.beginDeleteSecret(secretId).waitForCompletion();
        } catch (ResourceNotFoundException e) {
            throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                    "deleteSecret", SECRET, secretId, e);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "deleteSecret", SECRET, secretId);
        }
    }

    @Override
    public ListResponse<SecretMetadata> listSecrets(ListRequest<SecretMetadata> request) {
        // Key Vault uses cursor-based pagination; offset-based requires materializing
        // all results
        try {
            PagedIterable<SecretProperties> paged = secretClient.listPropertiesOfSecrets();
            List<SecretMetadata> all = new ArrayList<>();
            paged.forEach(props -> {
                long createdAt = props.getCreatedOn() != null ? props.getCreatedOn().toInstant().toEpochMilli() : 0L;
                long updated = props.getUpdatedOn() != null ? props.getUpdatedOn().toInstant().toEpochMilli() : 0L;
                all.add(new SecretMetadata(props.getName(), props.getName(), null, null, createdAt, updated));
            });

            int total = all.size();
            int start = request.offset() != null ? Math.max(0, Math.min(request.offset(), total)) : 0;
            int end = request.limit() != null ? Math.min(start + request.limit(), total) : total;
            return new ListResponse<>(all.subList(start, end), null, end < total);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "listSecrets", SECRET, null);
        }
    }
}
