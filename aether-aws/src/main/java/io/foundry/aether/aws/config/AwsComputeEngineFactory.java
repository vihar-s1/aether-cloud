/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.compute.AwsEc2ComputeEngine;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;

/** Creates an {@link AwsEc2ComputeEngine} from an {@link AwsProviderConfig}. */
public final class AwsComputeEngineFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return ComputeEngine.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new AwsEc2ComputeEngine(new AwsCloudProvider((AwsProviderConfig) config));
    }
}
