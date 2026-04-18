/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ProviderUnavailableException;

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
        if (status == ProviderStatus.SHUTDOWN) {
            throw new InvalidConfigurationException(PROVIDER_NAME, "initialize", "Provider has been shut down");
        }
        if (status == ProviderStatus.RUNNING) {
            throw new InvalidConfigurationException(PROVIDER_NAME, "initialize", "Provider is already running");
        }
        status = ProviderStatus.RUNNING;
    }

    @Override
    public void shutdown() {
        if (status == ProviderStatus.SHUTDOWN) {
            throw new ProviderUnavailableException(PROVIDER_NAME, "shutdown", "Provider is already shut down");
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
