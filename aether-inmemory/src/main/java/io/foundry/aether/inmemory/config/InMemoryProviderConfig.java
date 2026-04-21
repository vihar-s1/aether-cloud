/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.inmemory.InMemoryCloudProvider;

/** Configuration for an in-memory provider instance. */
public final class InMemoryProviderConfig implements ProviderConfig {

    private final String name;

    private InMemoryProviderConfig(String name) {
        this.name = name;
    }

    public static InMemoryProviderConfig of(String alias) {
        return new InMemoryProviderConfig(alias);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }
}
