/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import io.foundry.aether.inmemory.storage.InMemoryBlobStore;

/**
 * Creates an {@link InMemoryBlobStore} from an {@link InMemoryProviderConfig}.
 */
public final class InMemoryBlobStoreFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return BlobStore.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new InMemoryBlobStore(new InMemoryCloudProvider());
    }
}
