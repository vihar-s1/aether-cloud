/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import io.foundry.aether.core.secrets.AbstractSecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySecretManager extends AbstractSecretManager<InMemoryCloudProvider> {

    private final ConcurrentHashMap<String, SecretEntry> secrets = new ConcurrentHashMap<>();

    public InMemorySecretManager(InMemoryCloudProvider provider) {
        super(provider);
    }

    @Override
    protected SecretEntry readEntry(String secretId) {
        return secrets.getOrDefault(secretId, null);
    }

    @Override
    protected Collection<SecretMetadata> listEntries() {
        return secrets.values().stream().map(SecretEntry::metadata).toList();
    }

    @Override
    protected SecretMetadata findSecretMetadata(String secretId) {
        return Optional.ofNullable(secrets.get(secretId))
                .map(SecretEntry::metadata)
                .orElse(null);
    }

    @Override
    protected VersionInfo createEntry(String secretId, String value) {
        String versionId = String.valueOf(System.nanoTime());
        long createdAt = System.currentTimeMillis();
        SecretMetadata metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, 0L);
        secrets.put(secretId, SecretEntry.of(value, metadata));
        return new VersionInfo(versionId, createdAt);
    }

    @Override
    protected VersionInfo updateEntry(String secretId, String value) {
        SecretEntry existing = secrets.get(secretId);
        String versionId = String.valueOf(System.nanoTime());
        long updatedAt = System.currentTimeMillis();
        SecretMetadata newMetadata = existing.metadata().updateVersion(versionId);
        secrets.put(secretId, SecretEntry.of(value, newMetadata));
        return new VersionInfo(versionId, updatedAt);
    }

    @Override
    protected SecretEntry deleteEntry(String secretId) {
        return secrets.remove(secretId);
    }
}
