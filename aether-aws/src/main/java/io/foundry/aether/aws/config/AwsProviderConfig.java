/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
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

public final class AwsProviderConfig implements ProviderConfig {

    private final String name;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String endpoint;
    private final Set<Class<? extends CloudService>> enabledServices;

    private AwsProviderConfig(Builder b) {
        this.name = b.name;
        this.accessKey = b.accessKey;
        this.secretKey = b.secretKey;
        this.region = b.region;
        this.endpoint = b.endpoint;
        this.enabledServices = Collections.unmodifiableSet(new LinkedHashSet<>(b.enabledServices));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    @Override
    public Set<Class<? extends CloudService>> enabledServices() {
        return enabledServices;
    }

    public Optional<String> accessKey() {
        return Optional.ofNullable(accessKey);
    }

    public Optional<String> secretKey() {
        return Optional.ofNullable(secretKey);
    }

    public String region() {
        return region;
    }

    public Optional<String> endpoint() {
        return Optional.ofNullable(endpoint);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name = AwsCloudProvider.PROVIDER_NAME;
        private String accessKey;
        private String secretKey;
        private String region;
        private String endpoint;
        private final Set<Class<? extends CloudService>> enabledServices = new LinkedHashSet<>();

        public Builder name(String v) {
            this.name = v;
            return this;
        }

        public Builder accessKey(String v) {
            this.accessKey = v;
            return this;
        }

        public Builder secretKey(String v) {
            this.secretKey = v;
            return this;
        }

        public Builder region(String v) {
            this.region = v;
            return this;
        }

        public Builder endpoint(String v) {
            this.endpoint = (v == null || v.isBlank()) ? null : v;
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

        public AwsProviderConfig build() {
            _require("region", region);
            boolean hasKey = accessKey != null && !accessKey.isBlank();
            boolean hasSecret = secretKey != null && !secretKey.isBlank();
            if (hasKey != hasSecret) {
                throw new InvalidConfigurationException("aws", "config",
                        "access-key and secret-key must both be provided or both be absent");
            }
            return new AwsProviderConfig(this);
        }

        private void _require(String field, String value) {
            if (value == null || value.isBlank()) {
                throw new InvalidConfigurationException("aws", "config",
                        "Required field '" + field + "' is missing or blank");
            }
        }
    }
}
