/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.gcp.config.GcpProviderConfig;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * GCP provider instance. Owns lazily-built SDK clients for Storage, Secret
 * Manager, and Compute.
 *
 * <p>
 * Lifecycle:
 * <ol>
 * <li>Construct with a {@link GcpProviderConfig}.
 * <li>{@link #initialize()} — validates lifecycle state and marks the provider
 * {@code RUNNING}.
 * <li>Use — service implementations call {@link #storageClient()},
 * {@link #secretManagerClient()}, or {@link #instancesClient()}; each client is
 * built lazily on first access.
 * <li>{@link #shutdown()} — closes any clients that were built.
 * </ol>
 *
 * <p>
 * Client building is lazy because GCP has no single emulator that covers all
 * three services. Providers used only for Storage (for example) never attempt
 * to build Secret Manager or Compute clients, so missing credentials for unused
 * services do not cause failures.
 */
public class GcpCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "gcp";

    private final String alias;
    private final GcpProviderConfig config;

    private volatile ProviderStatus status = ProviderStatus.UNINITIALIZED;
    private volatile Throwable failureCause;
    private volatile Storage storageClient;
    private volatile SecretManagerServiceClient secretManagerClient;
    private volatile InstancesClient instancesClient;

    public GcpCloudProvider(GcpProviderConfig config) {
        this.alias = config.name();
        this.config = config;
    }

    @Override
    public String name() {
        return alias;
    }

    @Override
    public synchronized void initialize() {
        if (status == ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is already running");
        }
        if (status == ProviderStatus.SHUTDOWN) {
            throw new IllegalStateException(
                    "Provider '" + alias + "' has been shut down — create a new instance to reuse");
        }
        failureCause = null;
        status = ProviderStatus.RUNNING;
    }

    @Override
    public synchronized void shutdown() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' cannot be shut down from status: " + status);
        }
        if (storageClient != null) {
            try {
                storageClient.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (secretManagerClient != null) {
            secretManagerClient.close();
        }
        if (instancesClient != null) {
            instancesClient.close();
        }
        status = ProviderStatus.SHUTDOWN;
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    public GcpProviderConfig config() {
        return config;
    }

    public Storage storageClient() {
        checkRunning();
        if (storageClient == null) {
            synchronized (this) {
                if (storageClient == null) {
                    storageClient = buildStorageClient();
                }
            }
        }
        return storageClient;
    }

    public SecretManagerServiceClient secretManagerClient() {
        checkRunning();
        if (secretManagerClient == null) {
            synchronized (this) {
                if (secretManagerClient == null) {
                    secretManagerClient = buildSecretManagerClient();
                }
            }
        }
        return secretManagerClient;
    }

    public InstancesClient instancesClient() {
        checkRunning();
        if (instancesClient == null) {
            synchronized (this) {
                if (instancesClient == null) {
                    instancesClient = buildInstancesClient();
                }
            }
        }
        return instancesClient;
    }

    private Storage buildStorageClient() {
        StorageOptions.Builder builder = StorageOptions.newBuilder().setProjectId(config.projectId())
                .setCredentials(loadCredentials());
        config.storageEndpoint().ifPresent(builder::setHost);
        return builder.build().getService();
    }

    private SecretManagerServiceClient buildSecretManagerClient() {
        try {
            SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(loadCredentials())).build();
            return SecretManagerServiceClient.create(settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build GCP Secret Manager client for provider '" + alias + "'", e);
        }
    }

    private InstancesClient buildInstancesClient() {
        try {
            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(loadCredentials())).build();
            return InstancesClient.create(settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build GCP Instances client for provider '" + alias + "'", e);
        }
    }

    private GoogleCredentials loadCredentials() {
        try {
            if (config.credentialsPath().isPresent()) {
                try (InputStream is = new FileInputStream(config.credentialsPath().get())) {
                    return GoogleCredentials.fromStream(is)
                            .createScoped("https://www.googleapis.com/auth/cloud-platform");
                }
            }
            return GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GCP credentials for provider '" + alias + "'", e);
        }
    }

    private void checkRunning() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is not running (status: " + status + ")"
                    + (failureCause != null ? " — failure: " + failureCause.getMessage() : ""));
        }
    }
}
