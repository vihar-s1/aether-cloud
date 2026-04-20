/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderConfigFactory;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.util.Map;

/** SPI factory that always returns {@link InMemoryProviderConfig#INSTANCE}. */
public final class InMemoryProviderConfigFactory implements ProviderConfigFactory {

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig create(Map<String, String> rawProperties) {
        return InMemoryProviderConfig.INSTANCE;
    }
}
