/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws;

import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS provider instance. Owns the SDK clients for a single configured AWS
 * account/region.
 *
 * <p>
 * Call {@link #initialize()} before using any service. Call {@link #shutdown()}
 * when done to close all clients.
 */
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
            s3Client = AwsUtils.applyCommonConfig(S3Client.builder(), config).forcePathStyle(true).build();
            secretsManagerClient = AwsUtils.applyCommonConfig(SecretsManagerClient.builder(), config).build();
            ec2Client = AwsUtils.applyCommonConfig(Ec2Client.builder(), config).build();
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
        s3Client.close();
        secretsManagerClient.close();
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

    public S3Client s3Client() {
        checkRunning();
        return s3Client;
    }

    public SecretsManagerClient secretsManagerClient() {
        checkRunning();
        return secretsManagerClient;
    }

    public Ec2Client ec2Client() {
        checkRunning();
        return ec2Client;
    }

    private void checkRunning() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is not running (status: " + status + ")"
                    + (failureCause != null ? " — failure: " + failureCause.getMessage() : ""));
        }
    }
}
