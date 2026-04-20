/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.secrets.NFSSecretManager;

/** Creates an {@link NFSSecretManager} from an {@link NfsProviderConfig}. */
public final class NfsSecretManagerFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return NFSCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return SecretManager.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new NFSSecretManager(new NFSCloudProvider((NfsProviderConfig) config));
    }
}
