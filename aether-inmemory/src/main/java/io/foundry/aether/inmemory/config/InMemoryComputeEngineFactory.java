/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import io.foundry.aether.inmemory.compute.InMemoryComputeEngine;

/**
 * Creates an {@link InMemoryComputeEngine} from an
 * {@link InMemoryProviderConfig}.
 */
public final class InMemoryComputeEngineFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return ComputeEngine.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new InMemoryComputeEngine(new InMemoryCloudProvider());
    }
}
