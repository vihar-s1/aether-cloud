/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.config;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.util.Optional;

/** Configuration for an AWS provider instance. */
public final class AwsProviderConfig implements ProviderConfig {

    private final String name;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String endpoint; // nullable — uses AWS default when absent

    private AwsProviderConfig(Builder b) {
        this.name = b.name;
        this.accessKey = b.accessKey;
        this.secretKey = b.secretKey;
        this.region = b.region;
        this.endpoint = b.endpoint;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return AwsCloudProvider.PROVIDER_NAME;
    }

    /**
     * Returns the explicit access key, or empty to use the default credential chain
     * (IAM/env).
     */
    public Optional<String> accessKey() {
        return Optional.ofNullable(accessKey);
    }

    /**
     * Returns the explicit secret key, or empty to use the default credential chain
     * (IAM/env).
     */
    public Optional<String> secretKey() {
        return Optional.ofNullable(secretKey);
    }

    public String region() {
        return region;
    }

    /** Returns the endpoint override URL, or empty to use the AWS default. */
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
