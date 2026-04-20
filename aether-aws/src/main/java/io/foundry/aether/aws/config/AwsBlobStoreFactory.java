/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.storage.AwsS3BlobStore;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.storage.BlobStore;

/** Creates an {@link AwsS3BlobStore} from an {@link AwsProviderConfig}. */
public final class AwsBlobStoreFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return BlobStore.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new AwsS3BlobStore(new AwsCloudProvider((AwsProviderConfig) config));
    }
}
