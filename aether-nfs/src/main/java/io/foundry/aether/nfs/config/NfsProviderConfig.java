/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.nio.file.Path;

/** Configuration for an NFS (filesystem-backed) provider instance. */
public final class NfsProviderConfig implements ProviderConfig {

    private final String name;
    private final Path rootPath;

    private NfsProviderConfig(String name, Path rootPath) {
        this.name = name;
        this.rootPath = rootPath;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return NFSCloudProvider.PROVIDER_NAME;
    }

    public Path rootPath() {
        return rootPath;
    }

    public static NfsProviderConfig of(String alias, Path rootPath) {
        if (rootPath == null) {
            throw new InvalidConfigurationException("nfs", "config", "Required field 'root-path' must not be null");
        }
        return new NfsProviderConfig(alias, rootPath);
    }

    public static NfsProviderConfig of(String alias, String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new InvalidConfigurationException("nfs", "config", "Required field 'root-path' is missing or blank");
        }
        return new NfsProviderConfig(alias, Path.of(rootPath));
    }
}
