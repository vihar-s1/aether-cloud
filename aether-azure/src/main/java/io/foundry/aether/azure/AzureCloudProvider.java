/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.foundry.aether.azure.config.AzureProviderConfig;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ProviderStatus;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.storage.BlobStore;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AzureCloudProvider implements CloudProvider {

    public static final String PROVIDER_NAME = "azure";

    private final String alias;
    private final AzureProviderConfig config;

    private volatile ProviderStatus status = ProviderStatus.UNINITIALIZED;
    private volatile Throwable failureCause;
    private volatile TokenCredential credential;
    private volatile BlobServiceClient blobServiceClient;
    private volatile SecretClient secretClient;
    private volatile ComputeManager computeManager;

    public AzureCloudProvider(AzureProviderConfig config) {
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
            credential = buildCredential();
            if (config.isEnabled(BlobStore.class)) {
                blobServiceClient = buildBlobServiceClient();
            }
            if (config.isEnabled(SecretManager.class)) {
                String vaultUrl = config.keyVaultUrl()
                        .orElseThrow(() -> new InvalidConfigurationException(PROVIDER_NAME, "init",
                                "'key-vault-url' is required when SecretManager is enabled"));
                secretClient = new SecretClientBuilder().vaultUrl(vaultUrl).credential(credential).buildClient();
            }
            if (config.isEnabled(ComputeEngine.class)) {
                String subscriptionId = config.subscriptionId()
                        .orElseThrow(() -> new InvalidConfigurationException(PROVIDER_NAME, "init",
                                "'subscription-id' is required when ComputeEngine is enabled"));
                config.resourceGroup().orElseThrow(() -> new InvalidConfigurationException(PROVIDER_NAME, "init",
                        "'resource-group' is required when ComputeEngine is enabled"));
                AzureProfile profile = new AzureProfile(config.tenantId().orElse(null), subscriptionId,
                        AzureEnvironment.AZURE);
                computeManager = ComputeManager.authenticate(credential, profile);
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

    public AzureProviderConfig config() {
        return config;
    }

    public BlobServiceClient blobServiceClient() {
        checkRunning();
        if (blobServiceClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "blobServiceClient",
                    "BlobStore was not enabled — call .enable(BlobStore.class) on the config builder");
        return blobServiceClient;
    }

    public SecretClient secretClient() {
        checkRunning();
        if (secretClient == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "secretClient",
                    "SecretManager was not enabled — call .enable(SecretManager.class) on the config builder");
        return secretClient;
    }

    public ComputeManager computeManager() {
        checkRunning();
        if (computeManager == null)
            throw new InvalidConfigurationException(PROVIDER_NAME, "computeManager",
                    "ComputeEngine was not enabled — call .enable(ComputeEngine.class) on the config builder");
        return computeManager;
    }

    public TokenCredential credential() {
        checkRunning();
        return credential;
    }

    private TokenCredential buildCredential() {
        if (config.clientId().isPresent() && config.clientSecret().isPresent() && config.tenantId().isPresent()) {
            return new ClientSecretCredentialBuilder().tenantId(config.tenantId().get())
                    .clientId(config.clientId().get()).clientSecret(config.clientSecret().get()).build();
        }
        return new DefaultAzureCredentialBuilder().build();
    }

    private BlobServiceClient buildBlobServiceClient() {
        if (config.noCredentials()) {
            String endpoint = config.storageEndpoint()
                    .orElseThrow(() -> new InvalidConfigurationException(PROVIDER_NAME, "init",
                            "'storage-endpoint' is required when 'no-credentials' is true"));
            String connectionString = "DefaultEndpointsProtocol=http;" + "AccountName=devstoreaccount1;"
                    + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
                    + "BlobEndpoint=" + endpoint + ";";
            return new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        }
        String endpoint = config.storageEndpoint()
                .orElse("https://" + config.storageAccount() + ".blob.core.windows.net");
        return new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).buildClient();
    }

    private void checkRunning() {
        if (status != ProviderStatus.RUNNING) {
            throw new IllegalStateException("Provider '" + alias + "' is not running (status: " + status + ")"
                    + (failureCause != null ? " — failure: " + failureCause.getMessage() : ""));
        }
    }
}
