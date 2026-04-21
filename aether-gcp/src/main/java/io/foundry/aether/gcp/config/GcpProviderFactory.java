/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderFactory;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.gcp.GcpCloudProvider;
import io.foundry.aether.gcp.compute.GcpComputeEngine;
import io.foundry.aether.gcp.secrets.GcpSecretManager;
import io.foundry.aether.gcp.storage.GcpBlobStore;
import java.util.Map;
import java.util.Optional;

/**
 * Unified SPI factory for the GCP provider — handles config and all service
 * types.
 */
public final class GcpProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return GcpCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return GcpProviderConfig.builder().name(alias).projectId(props.get("project-id"))
                .credentialsPath(props.get("credentials-path")).zone(props.get("zone"))
                .storageEndpoint(props.get("storage-endpoint")).build();
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        GcpCloudProvider provider = new GcpCloudProvider((GcpProviderConfig) config);
        provider.initialize();
        if (BlobStore.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new GcpBlobStore(provider)));
        }
        if (SecretManager.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new GcpSecretManager(provider)));
        }
        if (ComputeEngine.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new GcpComputeEngine(provider)));
        }
        return Optional.empty();
    }
}
