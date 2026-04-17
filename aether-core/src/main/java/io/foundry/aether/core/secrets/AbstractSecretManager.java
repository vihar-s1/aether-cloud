/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public abstract class AbstractSecretManager<T extends CloudProvider> implements SecretManager {

    protected record SecretEntry(String value, SecretMetadata metadata) {
        public static SecretEntry of(String value, SecretMetadata metadata) {
            return new SecretEntry(value, metadata);
        }
    }

    protected T cloudProvider;

    protected AbstractSecretManager(T cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        SecretEntry entry = readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "getSecret", secretId, "Secret not found");
        }
        return new SecretValue(
                secretId,
                entry.value(),
                entry.metadata().versionId(),
                entry.metadata().createdAt());
    }

    @Override
    public SecretMetadata putSecret(String secretId, String value) {
        SecretEntry entry = readEntry(secretId);
        String versionId =
                entry != null ? String.valueOf(Integer.parseInt(entry.metadata().versionId()) + 1) : "1";
        Instant createdAt = entry != null ? entry.metadata().createdAt() : Instant.now();
        Instant rotatedAt = entry != null ? entry.metadata().lastRotatedAt() : null;
        var metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, rotatedAt);
        upsertEntry(secretId, new SecretEntry(value, metadata));
        return metadata;
    }

    @Override
    public SecretValue rotate(String secretId) {
        SecretEntry entry = readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(cloudProvider.name(), "rotate", secretId, "Secret not found");
        }
        String newVersionId = String.valueOf(Integer.parseInt(entry.metadata().versionId()) + 1);
        Instant now = Instant.now();
        var metadata = new SecretMetadata(
                secretId, secretId, null, newVersionId, entry.metadata().createdAt(), now);
        upsertEntry(secretId, new SecretEntry(entry.value(), metadata));
        return new SecretValue(
                secretId, entry.value(), newVersionId, entry.metadata().createdAt());
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

    protected abstract void upsertEntry(String secretId, SecretEntry entry);

    protected abstract SecretEntry deleteEntry(String secretId);
}
