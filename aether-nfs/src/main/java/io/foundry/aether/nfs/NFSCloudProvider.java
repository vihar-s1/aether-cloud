/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ProviderUnavailableException;

public class NFSCloudProvider implements CloudProvider {

    private ProviderStatus status = ProviderStatus.INITIALIZED;
    private final String basePath;

    public NFSCloudProvider(String basePath) {
        this.basePath = basePath;
    }

    public String basePath() {
        return basePath;
    }

    @Override
    public String name() {
        return "nfs";
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public void initialize() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new InvalidConfigurationException("nfs", "initialize", "Provider has been shut down");
        }
        if (status == ProviderStatus.RUNNING) {
            throw new InvalidConfigurationException("nfs", "initialize", "Provider is already running");
        }
        status = ProviderStatus.RUNNING;
    }

    @Override
    public void shutdown() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new ProviderUnavailableException("nfs", "shutdown", "Provider is already shut down");
        }
        status = ProviderStatus.SHUTDOWN;
    }
}
