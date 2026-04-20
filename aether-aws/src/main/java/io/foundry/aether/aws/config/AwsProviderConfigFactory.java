/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderConfigFactory;
import java.util.Map;

/**
 * SPI factory that creates {@link AwsProviderConfig} from raw property maps.
 */
public final class AwsProviderConfigFactory implements ProviderConfigFactory {

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig create(Map<String, String> props) {
        return AwsProviderConfig.builder().accessKey(props.get("access-key")).secretKey(props.get("secret-key"))
                .region(props.get("region")).endpoint(props.get("endpoint")).build();
    }
}
