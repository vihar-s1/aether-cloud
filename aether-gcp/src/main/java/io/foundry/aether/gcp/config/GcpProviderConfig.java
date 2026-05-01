/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.config;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.gcp.GcpCloudProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class GcpProviderConfig implements ProviderConfig {

    private final String name;
    private final String projectId;
    private final String credentialsPath;
    private final String zone;
    private final String storageEndpoint;
    private final String secretManagerEndpoint;
    private final boolean noCredentials;
    private final Set<Class<? extends CloudService>> enabledServices;

    private GcpProviderConfig(Builder b) {
        this.name = b.name;
        this.projectId = b.projectId;
        this.credentialsPath = b.credentialsPath;
        this.zone = b.zone;
        this.storageEndpoint = b.storageEndpoint;
        this.secretManagerEndpoint = b.secretManagerEndpoint;
        this.noCredentials = b.noCredentials;
        this.enabledServices = Collections.unmodifiableSet(new LinkedHashSet<>(b.enabledServices));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return GcpCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Set<Class<? extends CloudService>> enabledServices() {
        return enabledServices;
    }

    public String projectId() {
        return projectId;
    }

    public Optional<String> credentialsPath() {
        return Optional.ofNullable(credentialsPath);
    }

    public Optional<String> zone() {
        return Optional.ofNullable(zone);
    }

    public Optional<String> storageEndpoint() {
        return Optional.ofNullable(storageEndpoint);
    }

    public Optional<String> secretManagerEndpoint() {
        return Optional.ofNullable(secretManagerEndpoint);
    }

    public boolean noCredentials() {
        return noCredentials;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String projectId;
        private String credentialsPath;
        private String zone;
        private String storageEndpoint;
        private String secretManagerEndpoint;
        private boolean noCredentials = false;
        private final Set<Class<? extends CloudService>> enabledServices = new LinkedHashSet<>();

        public Builder name(String v) {
            this.name = v;
            return this;
        }

        public Builder projectId(String v) {
            this.projectId = v;
            return this;
        }

        public Builder credentialsPath(String v) {
            this.credentialsPath = v;
            return this;
        }

        public Builder zone(String v) {
            this.zone = v;
            return this;
        }

        public Builder storageEndpoint(String v) {
            this.storageEndpoint = v;
            return this;
        }

        public Builder secretManagerEndpoint(String v) {
            this.secretManagerEndpoint = v;
            return this;
        }

        public Builder noCredentials(boolean v) {
            this.noCredentials = v;
            return this;
        }

        @SafeVarargs
        public final Builder enable(Class<? extends CloudService>... serviceTypes) {
            enabledServices.addAll(Arrays.asList(serviceTypes));
            return this;
        }

        public Builder enableAll() {
            return enable(BlobStore.class, SecretManager.class, ComputeEngine.class);
        }

        public GcpProviderConfig build() {
            _require("project-id", projectId);
            return new GcpProviderConfig(this);
        }

        private void _require(String field, String value) {
            if (value == null || value.isBlank()) {
                throw new InvalidConfigurationException("gcp", "config",
                        "Required field '" + field + "' is missing or blank");
            }
        }
    }
}
