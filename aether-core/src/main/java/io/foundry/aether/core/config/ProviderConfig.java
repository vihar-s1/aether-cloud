/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import io.foundry.aether.core.CloudService;
import java.util.Set;

/** Marker interface for provider configuration objects. */
public interface ProviderConfig {

    String name();

    String providerType();

    default Set<Class<? extends CloudService>> enabledServices() {
        return Set.of();
    }

    default boolean isEnabled(Class<? extends CloudService> serviceType) {
        return enabledServices().contains(serviceType);
    }
}
