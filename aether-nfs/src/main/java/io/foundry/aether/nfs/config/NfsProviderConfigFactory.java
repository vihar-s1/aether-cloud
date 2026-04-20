/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderConfigFactory;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.util.Map;

/**
 * SPI factory that creates {@link NfsProviderConfig} from raw property maps.
 */
public final class NfsProviderConfigFactory implements ProviderConfigFactory {

    @Override
    public String providerType() {
        return NFSCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig create(Map<String, String> props) {
        return NfsProviderConfig.of(props.get("root-path"));
    }
}
