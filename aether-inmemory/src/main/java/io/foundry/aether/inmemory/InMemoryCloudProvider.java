/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class InMemoryCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "inmemory";

    private final String alias;

    private volatile ProviderStatus status = ProviderStatus.UNINITIALIZED;

    public InMemoryCloudProvider(String alias) {
        this.alias = alias;
    }

    @Override
    public String name() {
        return alias;
    }

    @Override
    public synchronized void initialize() {
        if (status == ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is already running");
        }
        if (status == ProviderStatus.SHUTDOWN) {
            throw new IllegalStateException(
                    "Provider '" + alias + "' has been shut down — create a new instance to reuse");
        }
        status = ProviderStatus.RUNNING;
    }

    @Override
    public synchronized void shutdown() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' cannot be shut down from status: " + status);
        }
        status = ProviderStatus.SHUTDOWN;
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public Optional<Throwable> failureCause() {
        return Optional.empty(); // in-memory provider never fails to initialize
    }
}
