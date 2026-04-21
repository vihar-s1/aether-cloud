/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import com.google.common.collect.ImmutableBiMap;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;

/**
 * Bi-directional registry of known service types.
 *
 * <p>
 * Maps service keys (YAML/env format) to their corresponding interfaces and
 * back. Adding a new service type to Aether requires exactly one entry here.
 *
 * <p>
 * Examples: {@code "blob-store"} ↔ {@link BlobStore}, {@code "secret-manager"}
 * ↔ {@link SecretManager}.
 */
public final class ServiceTypes {

    public static final ImmutableBiMap<String, Class<? extends CloudService>> REGISTRY = ImmutableBiMap
            .<String, Class<? extends CloudService>>builder().put("blob-store", BlobStore.class)
            .put("secret-manager", SecretManager.class).put("compute-engine", ComputeEngine.class).build();

    private ServiceTypes() {
    }
}
