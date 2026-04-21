/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderFactory;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.secrets.NFSSecretManager;
import io.foundry.aether.nfs.storage.NFSBlobStore;
import java.util.Map;
import java.util.Optional;

/**
 * Unified SPI factory for the NFS provider — handles config and all service
 * types.
 */
public final class NfsProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return NFSCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return NfsProviderConfig.of(alias, props.get("root-path"));
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        NFSCloudProvider provider = new NFSCloudProvider((NfsProviderConfig) config);
        provider.initialize();
        if (BlobStore.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new NFSBlobStore(provider)));
        }
        if (SecretManager.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new NFSSecretManager(provider)));
        }
        return Optional.empty();
    }
}
