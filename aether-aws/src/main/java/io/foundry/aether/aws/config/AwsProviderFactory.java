/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.compute.AwsEc2ComputeEngine;
import io.foundry.aether.aws.secrets.AwsSecretsManager;
import io.foundry.aether.aws.storage.AwsS3BlobStore;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderFactory;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import java.util.Map;
import java.util.Optional;

/**
 * Unified SPI factory for the AWS provider — handles config and all service
 * types.
 */
public final class AwsProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return AwsProviderConfig.builder().name(alias).accessKey(props.get("access-key"))
                .secretKey(props.get("secret-key")).region(props.get("region")).endpoint(props.get("endpoint")).build();
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        AwsCloudProvider provider = new AwsCloudProvider((AwsProviderConfig) config);
        provider.initialize();
        if (BlobStore.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AwsS3BlobStore(provider)));
        }
        if (SecretManager.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AwsSecretsManager(provider)));
        }
        if (ComputeEngine.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AwsEc2ComputeEngine(provider)));
        }
        return Optional.empty();
    }
}
