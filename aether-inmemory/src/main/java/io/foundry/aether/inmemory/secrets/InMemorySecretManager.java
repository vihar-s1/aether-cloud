/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import io.foundry.aether.core.secrets.AbstractSecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.util.Collection;
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
    protected void upsertEntry(String secretId, SecretEntry entry) {
        secrets.put(secretId, entry);
    }

    @Override
    protected SecretEntry deleteEntry(String secretId) {
        return secrets.remove(secretId);
    }
}
