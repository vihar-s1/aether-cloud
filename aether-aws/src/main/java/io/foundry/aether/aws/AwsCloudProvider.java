/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.exception.InvalidConfigurationException;

public class AwsCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "aws";

    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final String region;
    private volatile ProviderStatus status = ProviderStatus.INITIALIZED;

    public AwsCloudProvider(String accessKey, String secretKey, String endpoint, String region) {
        this.accessKey = nullSafe(accessKey, "accessKey");
        this.secretKey = nullSafe(secretKey, "secretKey");
        this.endpoint = endpoint;
        this.region = nullSafe(region, "region");
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public void initialize() {
        if (status != ProviderStatus.INITIALIZED) {
            throw new IllegalStateException("Provider already initialized");
        }
        status = ProviderStatus.RUNNING;
    }

    @Override
    public void shutdown() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new IllegalStateException("Provider already shutdown");
        }
        status = ProviderStatus.SHUTDOWN;
    }

    public String accessKey() {
        return accessKey;
    }

    public String secretKey() {
        return secretKey;
    }

    public String endpoint() {
        return endpoint;
    }

    public String region() {
        return region;
    }

    private String nullSafe(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new InvalidConfigurationException(PROVIDER_NAME, "initialize", name + " must not be null or empty");
        }
        return value;
    }
}
