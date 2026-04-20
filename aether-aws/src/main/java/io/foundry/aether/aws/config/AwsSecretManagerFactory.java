/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.secrets.AwsSecretsManager;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.config.CloudServiceFactory;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.secrets.SecretManager;

/** Creates an {@link AwsSecretsManager} from an {@link AwsProviderConfig}. */
public final class AwsSecretManagerFactory implements CloudServiceFactory {

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Class<? extends CloudService> serviceType() {
        return SecretManager.class;
    }

    @Override
    public CloudService create(ProviderConfig config) {
        return new AwsSecretsManager(new AwsCloudProvider((AwsProviderConfig) config));
    }
}
