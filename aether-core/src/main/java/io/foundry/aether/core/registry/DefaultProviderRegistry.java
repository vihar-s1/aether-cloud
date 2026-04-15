/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.registry;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.Validate;

public final class DefaultProviderRegistry implements ProviderRegistry {

    private final ConcurrentMap<String, CloudProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Class<?>, CloudService>> services = new ConcurrentHashMap<>();

    @Override
    public void register(CloudProvider provider) throws CloudException {
        Validate.notNull(provider, "provider must not be null");
        Validate.notBlank(provider.name(), "provider name must not be blank");

        if (providers.putIfAbsent(provider.name(), provider) != null) {
            throw new InvalidConfigurationException(
                    provider.name(), "register", "Provider already registered: " + provider.name());
        }
        services.putIfAbsent(provider.name(), new ConcurrentHashMap<>());
    }

    @Override
    public Optional<CloudProvider> getProvider(String name) {
        Validate.notBlank(name, "provider name must not be blank");
        return Optional.ofNullable(providers.get(name));
    }

    @Override
    public Collection<CloudProvider> listProviders() {
        return providers.values();
    }

    @Override
    public <S extends CloudService> void registerService(String providerName, Class<S> serviceType, S service)
            throws CloudException {
        Validate.notBlank(providerName, "provider name must not be blank");
        Validate.notNull(serviceType, "service type must not be null");
        Validate.notNull(service, "service must not be null");

        ConcurrentMap<Class<?>, CloudService> providerServices = services.get(providerName);
        if (providerServices == null) {
            throw new InvalidConfigurationException(
                    providerName, "registerService", "Unknown provider: " + providerName);
        }
        providerServices.put(serviceType, service);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends CloudService> Optional<S> getService(String providerName, Class<S> serviceType) {
        Validate.notBlank(providerName, "provider name must not be blank");
        Validate.notNull(serviceType, "service type must not be null");

        ConcurrentMap<Class<?>, CloudService> providerServices = services.get(providerName);
        if (providerServices == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((S) providerServices.get(serviceType));
    }
}
