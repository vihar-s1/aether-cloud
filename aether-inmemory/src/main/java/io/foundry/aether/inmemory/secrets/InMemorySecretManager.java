/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySecretManager implements SecretManager {

    private record SecretEntry(String value, String versionId, SecretMetadata metadata) {}

    private final CloudProvider provider;
    private final ConcurrentHashMap<String, SecretEntry> secrets = new ConcurrentHashMap<>();

    public InMemorySecretManager(CloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public String serviceName() {
        return "secret-manager";
    }

    @Override
    public SecretValue getSecret(String secretId) {
        SecretEntry entry = secrets.get(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException("inmemory", "getSecret", secretId, "Secret not found");
        }
        return new SecretValue(
                secretId, entry.value(), entry.versionId(), entry.metadata().createdAt());
    }

    @Override
    public SecretMetadata putSecret(String secretId, String value) {
        String versionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        SecretEntry existing = secrets.get(secretId);
        var metadata = new SecretMetadata(
                secretId, secretId, null, existing != null ? existing.metadata().createdAt() : now, now);
        secrets.put(secretId, new SecretEntry(value, versionId, metadata));
        return metadata;
    }

    @Override
    public SecretValue rotate(String secretId) {
        SecretEntry entry = secrets.get(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException("inmemory", "rotate", secretId, "Secret not found");
        }
        String newVersionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var metadata =
                new SecretMetadata(secretId, secretId, null, entry.metadata().createdAt(), now);
        secrets.put(secretId, new SecretEntry(entry.value(), newVersionId, metadata));
        return new SecretValue(secretId, entry.value(), newVersionId, now);
    }

    @Override
    public void deleteSecret(String secretId) {
        secrets.remove(secretId);
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        return secrets.values().stream().map(SecretEntry::metadata).toList();
    }
}
