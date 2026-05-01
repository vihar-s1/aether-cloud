/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.config;

import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.compute.AzureComputeEngine;
import io.foundry.aether.azure.secrets.AzureKeyVaultSecretManager;
import io.foundry.aether.azure.storage.AzureBlobStore;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.config.ProviderFactory;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import java.util.Map;
import java.util.Optional;

public final class AzureProviderFactory implements ProviderFactory {

    @Override
    public String providerType() {
        return AzureCloudProvider.PROVIDER_NAME;
    }

    @Override
    public ProviderConfig createConfig(String alias, Map<String, String> props) {
        return AzureProviderConfig.builder().name(alias).tenantId(props.get("tenant-id"))
                .clientId(props.get("client-id")).clientSecret(props.get("client-secret"))
                .subscriptionId(props.get("subscription-id")).resourceGroup(props.get("resource-group"))
                .storageAccount(props.get("storage-account")).storageEndpoint(props.get("storage-endpoint"))
                .keyVaultUrl(props.get("key-vault-url"))
                .noCredentials("true".equalsIgnoreCase(props.get("no-credentials"))).build();
    }

    @Override
    public <S extends CloudService> Optional<S> createService(ProviderConfig config, Class<S> serviceType) {
        AzureCloudProvider provider = new AzureCloudProvider((AzureProviderConfig) config);
        provider.initialize();
        if (BlobStore.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AzureBlobStore(provider)));
        }
        if (SecretManager.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AzureKeyVaultSecretManager(provider)));
        }
        if (ComputeEngine.class.isAssignableFrom(serviceType)) {
            return Optional.of(serviceType.cast(new AzureComputeEngine(provider)));
        }
        return Optional.empty();
    }
}
