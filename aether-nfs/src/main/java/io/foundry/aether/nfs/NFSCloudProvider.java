/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.nfs.config.NfsProviderConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class NFSCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "nfs";

    private final String alias;
    private final String basePath;

    private volatile ProviderStatus status = ProviderStatus.UNINITIALIZED;
    private volatile Throwable failureCause;

    public NFSCloudProvider(String alias, String basePath) {
        this.alias = alias;
        this.basePath = basePath;
    }

    public NFSCloudProvider(NfsProviderConfig config) {
        this.alias = config.name();
        this.basePath = config.rootPath().toString();
    }

    public String basePath() {
        return basePath;
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
        try {
            Path root = Path.of(basePath);
            if (!Files.exists(root)) {
                throw new IllegalArgumentException("Base path does not exist: " + basePath);
            }
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("Base path is not a directory: " + basePath);
            }
            failureCause = null;
            status = ProviderStatus.RUNNING;
        } catch (Exception e) {
            status = ProviderStatus.FAILED;
            failureCause = e;
            throw e;
        }
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
        return Optional.ofNullable(failureCause);
    }
}
