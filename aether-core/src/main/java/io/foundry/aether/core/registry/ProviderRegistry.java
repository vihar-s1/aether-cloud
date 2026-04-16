/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.registry;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.CloudService;
import java.util.Collection;
import java.util.Optional;

public interface ProviderRegistry {

    void register(CloudProvider provider);

    Optional<CloudProvider> getProvider(String name);

    Collection<CloudProvider> listProviders();

    <S extends CloudService> void registerService(String providerName, Class<S> serviceType, S service);

    <S extends CloudService> Optional<S> getService(String providerName, Class<S> serviceType);
}
