/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.nio.file.Path;
import java.util.Optional;

/** Configuration for an NFS (filesystem-backed) provider instance. */
public final class NfsProviderConfig implements ProviderConfig {

    private final String name;
    private final Path rootPath;
    private final String indexSecret;

    private NfsProviderConfig(String name, Path rootPath, String indexSecret) {
        this.name = name;
        this.rootPath = rootPath;
        this.indexSecret = indexSecret;
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

    /**
     * AES-256-GCM key material for encrypting {@code .aether-index} files. When
     * present, all index files are written as {@code [12-byte IV][ciphertext]}.
     * When absent, indices are plain UTF-8 JSON.
     */
    public Optional<String> indexSecret() {
        return Optional.ofNullable(indexSecret);
    }

    public static NfsProviderConfig of(String alias, String rootPath) {
        return of(alias, rootPath, null);
    }

    public static NfsProviderConfig of(String alias, String rootPath, String indexSecret) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new InvalidConfigurationException("nfs", "config", "Required field 'root-path' is missing or blank");
        }
        return new NfsProviderConfig(alias, Path.of(rootPath), indexSecret);
    }
}
