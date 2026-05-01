/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@ThreadSafe
public class AwsCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "aws";

    private final String alias;
    private final AwsProviderConfig config;

    private volatile ProviderStatus status = ProviderStatus.UNINITIALIZED;
    private volatile Throwable failureCause;
    private volatile S3Client s3Client;
    private volatile SecretsManagerClient secretsManagerClient;
    private volatile Ec2Client ec2Client;

    public AwsCloudProvider(AwsProviderConfig config) {
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
            if (config.isEnabled(BlobStore.class)) {
                s3Client = AwsUtils.applyCommonConfig(S3Client.builder(), config).forcePathStyle(true).build();
            }
            if (config.isEnabled(SecretManager.class)) {
                secretsManagerClient = AwsUtils.applyCommonConfig(SecretsManagerClient.builder(), config).build();
            }
            if (config.isEnabled(ComputeEngine.class)) {
                ec2Client = AwsUtils.applyCommonConfig(Ec2Client.builder(), config).build();
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
        if (s3Client != null)
            s3Client.close();
        if (secretsManagerClient != null)
            secretsManagerClient.close();
        if (ec2Client != null)
            ec2Client.close();
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

    public AwsProviderConfig config() {
        return config;
    }

    public S3Client s3Client() {
        checkRunning();
        if (s3Client == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "s3Client",
                    "BlobStore was not enabled — call .enable(BlobStore.class) on the config builder");
        return s3Client;
    }

    public SecretsManagerClient secretsManagerClient() {
        checkRunning();
        if (secretsManagerClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "secretsManagerClient",
                    "SecretManager was not enabled — call .enable(SecretManager.class) on the config builder");
        return secretsManagerClient;
    }

    public Ec2Client ec2Client() {
        checkRunning();
        if (ec2Client == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "ec2Client",
                    "ComputeEngine was not enabled — call .enable(ComputeEngine.class) on the config builder");
        return ec2Client;
    }

    private void checkRunning() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is not running (status: " + status + ")"
                    + (failureCause != null ? " — failure: " + failureCause.getMessage() : ""));
        }
    }
}
