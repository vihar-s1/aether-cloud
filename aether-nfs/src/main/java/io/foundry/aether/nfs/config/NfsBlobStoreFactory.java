/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.storage.NFSBlobStore;

/** Creates an {@link NFSBlobStore} from an {@link NfsProviderConfig}. */
public final class NfsBlobStoreFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return NFSCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return BlobStore.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new NFSBlobStore(new NFSCloudProvider((NfsProviderConfig) config));
    }
}
