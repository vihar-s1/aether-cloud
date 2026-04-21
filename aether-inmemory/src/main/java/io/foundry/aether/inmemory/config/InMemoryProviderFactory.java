/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderFactory;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import io.foundry.aether.inmemory.compute.InMemoryComputeEngine;
import io.foundry.aether.inmemory.secrets.InMemorySecretManager;
import io.foundry.aether.inmemory.storage.InMemoryBlobStore;
import java.util.Map;
import java.util.Optional;

/**
 * Unified SPI factory for the in-memory provider — handles config and all
 * service types.
 */
public final class InMemoryProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return InMemoryProviderConfig.of(alias);
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        InMemoryCloudProvider provider = new InMemoryCloudProvider();
        // Each call produces a new, independent service instance. If you need a
        // shared in-memory store across multiple calls, hold onto the returned
        // instance.
        if (BlobStore.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new InMemoryBlobStore(provider)));
        }
        if (SecretManager.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new InMemorySecretManager(provider)));
        }
        if (ComputeEngine.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new InMemoryComputeEngine(provider)));
        }
        return Optional.empty();
    }
}
