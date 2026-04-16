/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ProviderUnavailableException;

public class InMemoryCloudProvider implements CloudProvider {

    private volatile ProviderStatus status = ProviderStatus.INITIALIZED;

    @Override
    public String name() {
        return "inmemory";
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public void initialize() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new InvalidConfigurationException("inmemory", "initialize", "Provider has been shut down");
        }
        if (status == ProviderStatus.RUNNING) {
            throw new InvalidConfigurationException("inmemory", "initialize", "Provider is already running");
        }
        status = ProviderStatus.RUNNING;
    }

    @Override
    public void shutdown() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new ProviderUnavailableException("inmemory", "shutdown", "Provider is already shut down");
        }
        status = ProviderStatus.SHUTDOWN;
    }
}
