/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import java.util.Map;

/** Test-only SPI factory for the "stub" provider type. */
public final class StubProviderConfigFactory implements ProviderConfigFactory {

    @Override
    public String providerType() {
        return "stub";
    }

    @Override
    public ProviderConfig create(Map<String, String> rawProperties) {
        return new StubProviderConfig(rawProperties.getOrDefault("value", ""));
    }
}
