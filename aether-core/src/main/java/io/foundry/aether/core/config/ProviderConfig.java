/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

/** Marker interface for provider configuration objects. */
public interface ProviderConfig {

    /**
     * Logical alias assigned to this provider, e.g. {@code "prod-aws"},
     * {@code "local-nfs"}.
     */
    String name();

    /**
     * Provider type identifier, e.g. {@code "aws"}, {@code "nfs"},
     * {@code "inmemory"}.
     */
    String providerType();
}
