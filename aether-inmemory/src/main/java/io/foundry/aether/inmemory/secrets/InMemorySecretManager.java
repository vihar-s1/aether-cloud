/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySecretManager implements SecretManager {

    private final InMemoryCloudProvider provider;
    private final ConcurrentHashMap<String, Entry> secrets = new ConcurrentHashMap<>();

    private record Entry(String value, SecretMetadata metadata) {}

    public InMemorySecretManager(InMemoryCloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        Entry entry = secrets.get(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(provider.name(), "getSecret", SECRET, secretId);
        }
        return new SecretValue(
                secretId,
                entry.value(),
                entry.metadata().versionId(),
                entry.metadata().createdAtMs());
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        if (secrets.containsKey(secretId)) {
            throw new InvalidConfigurationException(
                    provider.name(), "createSecret", "Secret already exists: " + secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        long createdAt = System.currentTimeMillis();
        SecretMetadata metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, 0L);
        secrets.put(secretId, new Entry(value, metadata));
        return metadata;
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        Entry existing = secrets.get(secretId);
        if (existing == null) {
            throw new ResourceNotFoundException(provider.name(), "updateSecret", SECRET, secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        SecretMetadata newMetadata = existing.metadata().updateVersion(versionId);
        secrets.put(secretId, new Entry(value, newMetadata));
        return newMetadata;
    }

    @Override
    public SecretValue rotate(String secretId) {
        Entry entry = secrets.get(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(provider.name(), "rotate", SECRET, secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        SecretMetadata newMetadata = entry.metadata().updateVersion(versionId);
        secrets.put(secretId, new Entry(entry.value(), newMetadata));
        return new SecretValue(
                secretId, entry.value(), versionId, entry.metadata().createdAtMs());
    }

    @Override
    public void deleteSecret(String secretId) {
        Entry removed = secrets.remove(secretId);
        if (removed == null) {
            throw new ResourceNotFoundException(provider.name(), "deleteSecret", SECRET, secretId);
        }
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        return secrets.values().stream().map(Entry::metadata).toList();
    }
}
