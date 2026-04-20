/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Immutable top-level configuration holder.
 *
 * <p>
 * Maps logical provider names (e.g. {@code "prod-aws"}) to their
 * {@link ProviderConfig}, and optionally maps service types to provider aliases
 * for zero-code cloud switching.
 *
 * <p>
 * Build programmatically via {@link #builder()} or load from files/env via
 * {@link AetherConfigLoader}.
 */
public final class AetherConfig {

    private final Map<String, ProviderConfig> providers;
    private final Map<String, String> serviceRouting; // service key → provider alias

    AetherConfig(Map<String, ProviderConfig> providers, Map<String, String> serviceRouting) {
        this.providers = Map.copyOf(providers);
        this.serviceRouting = Map.copyOf(serviceRouting);
    }

    /**
     * Returns the provider config for the given name, cast to the expected type.
     *
     * @throws InvalidConfigurationException
     *             if the name is not configured or the type doesn't match
     */
    public <C extends ProviderConfig> C require(String name, Class<C> type) {
        ProviderConfig cfg = providers.get(name);
        if (cfg == null) {
            throw new InvalidConfigurationException("aether", "config.require",
                    "No provider configured with name: " + name);
        }
        if (!type.isInstance(cfg)) {
            throw new InvalidConfigurationException("aether", "config.require", "Provider '" + name + "' is of type "
                    + cfg.getClass().getSimpleName() + ", not " + type.getSimpleName());
        }
        return type.cast(cfg);
    }

    /**
     * Returns the provider config for the given name if present and of the expected
     * type.
     */
    public <C extends ProviderConfig> Optional<C> find(String name, Class<C> type) {
        ProviderConfig cfg = providers.get(name);
        if (!type.isInstance(cfg)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(cfg));
    }

    /** Returns all configured provider names. */
    public Set<String> providerNames() {
        return providers.keySet();
    }

    /** Returns true if a provider with the given name is configured. */
    public boolean hasProvider(String name) {
        return providers.containsKey(name);
    }

    /**
     * Creates a service by resolving the service routing config and delegating to
     * the appropriate {@link CloudServiceFactory}. Returns empty if no routing is
     * configured for this service type.
     *
     * <p>
     * Routing is resolved from env vars (highest priority) then YAML
     * {@code services:} section.
     */
    @SuppressWarnings("unchecked")
    public <S extends CloudService> Optional<S> createService(Class<S> serviceType) {
        String serviceKey = _serviceKey(serviceType);
        String providerAlias = serviceRouting.get(serviceKey);
        if (providerAlias == null) {
            return Optional.empty();
        }
        ProviderConfig providerConfig = providers.get(providerAlias);
        if (providerConfig == null) {
            throw new InvalidConfigurationException("aether", "createService",
                    "Service '" + serviceKey + "' routes to unknown provider: " + providerAlias);
        }
        CloudServiceFactory factory = _findServiceFactory(providerConfig.providerType(), serviceType);
        return Optional.of((S) factory.create(providerConfig));
    }

    /**
     * Like {@link #createService(Class)} but throws if no routing is configured.
     *
     * @throws InvalidConfigurationException
     *             if the service is not configured
     */
    public <S extends CloudService> S requireService(Class<S> serviceType) {
        return createService(serviceType)
                .orElseThrow(() -> new InvalidConfigurationException("aether", "requireService",
                        "No service routing configured for: " + serviceType.getSimpleName() + ". Add a 'services."
                                + _serviceKey(serviceType) + "' entry to aether.yml or set AETHER_SERVICE_"
                                + serviceType.getSimpleName().replaceAll("([A-Z])", "_$1").toUpperCase().substring(1)
                                + " env var."));
    }

    /** Returns the service routing map (service key → provider alias). */
    Map<String, String> serviceRouting() {
        return serviceRouting;
    }

    /** Returns the providers map. */
    Map<String, ProviderConfig> providers() {
        return providers;
    }

    /** Single-provider shortcut for simple setups. */
    public static AetherConfig of(String name, ProviderConfig config) {
        return builder().provider(name, config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        private final Map<String, String> serviceRouting = new LinkedHashMap<>();

        public Builder provider(String name, ProviderConfig config) {
            providers.put(name, config);
            return this;
        }

        /**
         * Route a service type to a named provider, e.g.
         * {@code route(BlobStore.class, "prod-aws")}.
         */
        public Builder route(Class<? extends CloudService> serviceType, String providerAlias) {
            serviceRouting.put(_serviceKey(serviceType), providerAlias);
            return this;
        }

        public AetherConfig build() {
            return new AetherConfig(providers, serviceRouting);
        }
    }

    private CloudServiceFactory _findServiceFactory(String providerType, Class<? extends CloudService> serviceType) {
        for (CloudServiceFactory factory : ServiceLoader.load(CloudServiceFactory.class,
                AetherConfig.class.getClassLoader())) {
            if (factory.providerType().equals(providerType) && factory.serviceType().isAssignableFrom(serviceType)) {
                return factory;
            }
        }
        throw new InvalidConfigurationException("aether", "createService",
                "No CloudServiceFactory found for provider type '" + providerType + "' and service type '"
                        + serviceType.getSimpleName() + "'");
    }

    /**
     * Converts a service class to a YAML/env key, e.g. {@code BlobStore} →
     * {@code "blob-store"}.
     */
    static String _serviceKey(Class<?> serviceType) {
        return serviceType.getSimpleName().replaceAll("([A-Z])", "-$1").toLowerCase().substring(1); // strip leading '-'
    }
}
