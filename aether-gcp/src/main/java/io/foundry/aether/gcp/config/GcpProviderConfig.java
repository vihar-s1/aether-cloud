/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.config;

import io.foundry.aether.core.config.ProviderConfig;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.gcp.GcpCloudProvider;
import java.util.Optional;

public final class GcpProviderConfig implements ProviderConfig {

    private final String name;
    private final String projectId;
    private final String credentialsPath;
    private final String zone;
    private final String storageEndpoint;
    private final boolean noCredentials;

    private GcpProviderConfig(Builder b) {
        this.name = b.name;
        this.projectId = b.projectId;
        this.credentialsPath = b.credentialsPath;
        this.zone = b.zone;
        this.storageEndpoint = b.storageEndpoint;
        this.noCredentials = b.noCredentials;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String providerType() {
        return GcpCloudProvider.PROVIDER_NAME;
    }

    public String projectId() {
        return projectId;
    }

    /**
     * Path to a service account JSON key file. Absent means Application Default
     * Credentials.
     */
    public Optional<String> credentialsPath() {
        return Optional.ofNullable(credentialsPath);
    }

    /** GCE zone for compute operations, e.g. {@code "us-central1-a"}. */
    public Optional<String> zone() {
        return Optional.ofNullable(zone);
    }

    /**
     * Custom host for the GCS client, e.g. {@code "http://localhost:4443"} when
     * targeting a local emulator or alternative GCS-compatible endpoint.
     * Credentials are still resolved via the normal chain regardless of this
     * setting.
     */
    public Optional<String> storageEndpoint() {
        return Optional.ofNullable(storageEndpoint);
    }

    /**
     * When {@code true}, skip credential loading entirely. Use this for
     * unauthenticated local emulators (e.g. fake-gcs-server in development or CI)
     * that do not validate credentials. Defaults to {@code false}.
     */
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
        private boolean noCredentials = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder credentialsPath(String credentialsPath) {
            this.credentialsPath = credentialsPath;
            return this;
        }

        public Builder zone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder storageEndpoint(String storageEndpoint) {
            this.storageEndpoint = storageEndpoint;
            return this;
        }

        public Builder noCredentials(boolean noCredentials) {
            this.noCredentials = noCredentials;
            return this;
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
