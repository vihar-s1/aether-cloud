/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

/** Test-only provider config with a single string value. */
final class StubProviderConfig implements ProviderConfig {

    final String value;

    StubProviderConfig(String value) {
        this.value = value;
    }

    @Override
    public String providerType() {
        return "stub";
    }
}
