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
                entry.metadata().createdAtMs());
    }

    @Override
    public SecretMetadata putSecret(String secretId, String value) {
        SecretEntry entry = readEntry(secretId);
        String versionId =
                entry != null ? String.valueOf(Integer.parseInt(entry.metadata().versionId()) + 1) : "1";
        long createdAtMs = entry != null ? entry.metadata().createdAtMs() : System.currentTimeMillis();
        long rotatedAtMs = entry != null ? entry.metadata().lastRotatedAtMs() : 0L;
        var metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAtMs, rotatedAtMs);
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
        long nowMs = System.currentTimeMillis();
        var metadata = new SecretMetadata(
                secretId, secretId, null, newVersionId, entry.metadata().createdAtMs(), nowMs);
        upsertEntry(secretId, new SecretEntry(entry.value(), metadata));
        return new SecretValue(
                secretId, entry.value(), newVersionId, entry.metadata().createdAtMs());
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
