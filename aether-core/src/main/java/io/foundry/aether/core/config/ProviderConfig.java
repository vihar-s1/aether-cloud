/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

/** Marker interface for provider-specific configuration objects. */
public interface ProviderConfig {

    /**
     * Provider type identifier, e.g. {@code "aws"}, {@code "nfs"},
     * {@code "inmemory"}.
     */
    String providerType();
}
