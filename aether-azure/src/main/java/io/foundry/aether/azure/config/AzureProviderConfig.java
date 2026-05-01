/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.config;

import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class AzureProviderConfig implements ProviderConfig {

    private final String name;
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String subscriptionId;
    private final String resourceGroup;
    private final String storageAccount;
    private final String storageEndpoint;
    private final String keyVaultUrl;
    private final boolean noCredentials;
    private final Set<Class<? extends CloudService>> enabledServices;

    private AzureProviderConfig(Builder b) {
        this.name = b.name;
        this.tenantId = b.tenantId;
        this.clientId = b.clientId;
        this.clientSecret = b.clientSecret;
        this.subscriptionId = b.subscriptionId;
        this.resourceGroup = b.resourceGroup;
        this.storageAccount = b.storageAccount;
        this.storageEndpoint = b.storageEndpoint;
        this.keyVaultUrl = b.keyVaultUrl;
        this.noCredentials = b.noCredentials;
        this.enabledServices = Collections.unmodifiableSet(new LinkedHashSet<>(b.enabledServices));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return AzureCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Set<Class<? extends CloudService>> enabledServices() {
        return enabledServices;
    }

    public Optional<String> tenantId() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<String> clientId() {
        return Optional.ofNullable(clientId);
    }

    public Optional<String> clientSecret() {
        return Optional.ofNullable(clientSecret);
    }

    public Optional<String> subscriptionId() {
        return Optional.ofNullable(subscriptionId);
    }

    public Optional<String> resourceGroup() {
        return Optional.ofNullable(resourceGroup);
    }

    public String storageAccount() {
        return storageAccount;
    }

    public Optional<String> storageEndpoint() {
        return Optional.ofNullable(storageEndpoint);
    }

    public Optional<String> keyVaultUrl() {
        return Optional.ofNullable(keyVaultUrl);
    }

    public boolean noCredentials() {
        return noCredentials;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name = AzureCloudProvider.PROVIDER_NAME;
        private String tenantId;
        private String clientId;
        private String clientSecret;
        private String subscriptionId;
        private String resourceGroup;
        private String storageAccount;
        private String storageEndpoint;
        private String keyVaultUrl;
        private boolean noCredentials;
        private final Set<Class<? extends CloudService>> enabledServices = new LinkedHashSet<>();

        public Builder name(String v) {
            this.name = v;
            return this;
        }
        public Builder tenantId(String v) {
            this.tenantId = v;
            return this;
        }
        public Builder clientId(String v) {
            this.clientId = v;
            return this;
        }
        public Builder clientSecret(String v) {
            this.clientSecret = v;
            return this;
        }
        public Builder subscriptionId(String v) {
            this.subscriptionId = v;
            return this;
        }
        public Builder resourceGroup(String v) {
            this.resourceGroup = v;
            return this;
        }
        public Builder storageAccount(String v) {
            this.storageAccount = v;
            return this;
        }

        public Builder storageEndpoint(String v) {
            this.storageEndpoint = (v == null || v.isBlank()) ? null : v;
            return this;
        }

        public Builder keyVaultUrl(String v) {
            this.keyVaultUrl = (v == null || v.isBlank()) ? null : v;
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

        public AzureProviderConfig build() {
            if (storageAccount == null || storageAccount.isBlank()) {
                throw new InvalidConfigurationException("azure", "config",
                        "Required field 'storage-account' is missing or blank");
            }
            return new AzureProviderConfig(this);
        }
    }
}
