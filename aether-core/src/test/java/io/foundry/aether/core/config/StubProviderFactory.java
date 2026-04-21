/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import io.foundry.aether.core.CloudService;
import java.util.Map;
import java.util.Optional;

/** Test-only SPI factory for the "stub" provider type. */
public final class StubProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return "stub";
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return new StubProviderConfig(alias, props.getOrDefault("value", ""));
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        return Optional.empty();
    }
}
