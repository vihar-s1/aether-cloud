/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.gcp.config.GcpProviderConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
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
        try {
            Credentials credentials = resolveCredentials();
            if (config.isEnabled(BlobStore.class)) {
                storageClient = buildStorageClient(credentials);
            }
            if (config.isEnabled(SecretManager.class)) {
                secretManagerClient = buildSecretManagerClient(credentials);
            }
            if (config.isEnabled(ComputeEngine.class)) {
                instancesClient = buildInstancesClient(credentials);
            }
            failureCause = null;
            status = ProviderStatus.RUNNING;
        } catch (Exception e) {
            status = ProviderStatus.FAILED;
            failureCause = e;
            throw e;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' cannot be shut down from status: " + status);
        }
        try {
            if (storageClient != null)
                storageClient.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (secretManagerClient != null)
            secretManagerClient.close();
        if (instancesClient != null)
            instancesClient.close();
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
        if (storageClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "storageClient",
                    "BlobStore was not enabled — call .enable(BlobStore.class) on the config builder");
        return storageClient;
    }

    public SecretManagerServiceClient secretManagerClient() {
        checkRunning();
        if (secretManagerClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "secretManagerClient",
                    "SecretManager was not enabled — call .enable(SecretManager.class) on the config builder");
        return secretManagerClient;
    }

    public InstancesClient instancesClient() {
        checkRunning();
        if (instancesClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "instancesClient",
                    "ComputeEngine was not enabled — call .enable(ComputeEngine.class) on the config builder");
        return instancesClient;
    }

    private Credentials resolveCredentials() {
        if (config.noCredentials()) {
            return NoCredentials.getInstance();
        }
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
            throw new InvalidConfigurationException(GcpCloudProvider.PROVIDER_NAME, "initialize",
                    "Failed to load GCP credentials: " + e.getMessage());
        }
    }

    private Storage buildStorageClient(Credentials credentials) {
        StorageOptions.Builder builder = StorageOptions.newBuilder().setProjectId(config.projectId())
                .setCredentials(credentials);
        config.storageEndpoint().ifPresent(builder::setHost);
        return builder.build().getService();
    }

    private SecretManagerServiceClient buildSecretManagerClient(Credentials credentials) {
        try {
            if (config.secretManagerEndpoint().isPresent() && config.noCredentials()) {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(config.secretManagerEndpoint().get())
                        .usePlaintext().build();
                SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                        .setTransportChannelProvider(
                                FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                        .setCredentialsProvider(NoCredentialsProvider.create()).build();
                return SecretManagerServiceClient.create(settings);
            }
            SecretManagerServiceSettings.Builder builder = SecretManagerServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials));
            config.secretManagerEndpoint().ifPresent(builder::setEndpoint);
            return SecretManagerServiceClient.create(builder.build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to build GCP Secret Manager client for provider '" + alias + "'", e);
        }
    }

    private InstancesClient buildInstancesClient(Credentials credentials) {
        try {
            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            return InstancesClient.create(settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build GCP Instances client for provider '" + alias + "'", e);
        }
    }

    private void checkRunning() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is not running (status: " + status + ")"
                    + (failureCause != null ? " — failure: " + failureCause.getMessage() : ""));
        }
    }
}
