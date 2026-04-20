/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import io.foundry.aether.inmemory.secrets.InMemorySecretManager;

/**
 * Creates an {@link InMemorySecretManager} from an
 * {@link InMemoryProviderConfig}.
 */
public final class InMemorySecretManagerFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return SecretManager.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new InMemorySecretManager(new InMemoryCloudProvider());
    }
}
