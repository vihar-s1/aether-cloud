/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.inmemory.InMemoryCloudProvider;

/** Singleton config for the in-memory provider — no fields required. */
public final class InMemoryProviderConfig implements ProviderConfig {

    public static final InMemoryProviderConfig INSTANCE = new InMemoryProviderConfig();

    private InMemoryProviderConfig() {
    }

    @Override
    public String providerType() {
        return InMemoryCloudProvider.PROVIDER_NAME;
    }
}
