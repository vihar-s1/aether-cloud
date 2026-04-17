/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.List;

public abstract class AbstractSecretManager<T extends CloudProvider> implements SecretManager {

    protected record SecretEntry(String value, SecretMetadata metadata) {
        public static SecretEntry of(String value, SecretMetadata metadata) {
            return new SecretEntry(value, metadata);
        }
    }

    public record VersionInfo(String versionId, long createdAtMs) {}

    protected T cloudProvider;

    protected AbstractSecretManager(T cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        SecretEntry entry = readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "getSecret", SECRET, secretId);
        }
        return new SecretValue(
                secretId,
                entry.value(),
                entry.metadata().versionId(),
                entry.metadata().createdAtMs());
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        SecretMetadata existingMetadata = findSecretMetadata(secretId);
        if (existingMetadata != null) {
            throw new IllegalStateException("Secret already exists: " + secretId);
        }
        VersionInfo versionInfo = createEntry(secretId, value);
        return new SecretMetadata(secretId, secretId, null, versionInfo.versionId(), versionInfo.createdAtMs(), 0L);
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        SecretMetadata existingMetadata = findSecretMetadata(secretId);
        if (existingMetadata == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "updateSecret", SECRET, secretId);
        }
        VersionInfo versionInfo = updateEntry(secretId, value);
        return existingMetadata.updateVersion(versionInfo.versionId());
    }

    @Override
    public SecretValue rotate(String secretId) {
        SecretEntry entry = readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "rotate", secretId, "Secret not found");
        }
        VersionInfo versionInfo = updateEntry(secretId, entry.value());
        return new SecretValue(
                secretId,
                entry.value(),
                versionInfo.versionId(),
                entry.metadata().createdAtMs());
    }

    @Override
    public void deleteSecret(String secretId) {
        if (deleteEntry(secretId) == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "deleteSecret", secretId, "Secret not found");
        }
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        return listEntries().stream().toList();
    }

    @Override
    public CloudProvider provider() {
        return cloudProvider;
    }

    protected abstract SecretEntry readEntry(String secretId);

    protected abstract Collection<SecretMetadata> listEntries();

    protected abstract SecretMetadata findSecretMetadata(String secretId);

    /**
     * Create secret entry. Returns version info (versionId and createdAt timestamp) assigned by
     * the provider.
     */
    protected abstract VersionInfo createEntry(String secretId, String value);

    /**
     * Update secret entry. Returns version info assigned by the provider.
     */
    protected abstract VersionInfo updateEntry(String secretId, String value);

    protected abstract SecretEntry deleteEntry(String secretId);
}
