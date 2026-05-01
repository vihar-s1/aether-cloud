/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.config.AzureProviderConfig;
import io.foundry.aether.core.contract.BlobStoreContractTest;
import io.foundry.aether.core.storage.BlobStore;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AzureBlobStoreIntegrationTest extends BlobStoreContractTest {

    private static final int BLOB_PORT = 10000;

    @Container
    private static final GenericContainer<?> azurite = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck").withExposedPorts(BLOB_PORT);

    @BeforeAll
    static void waitForAzurite() {
        // Container is started by @Testcontainers — just verify it's up
        assert azurite.isRunning();
    }

    private static String storageEndpoint() {
        return "http://" + azurite.getHost() + ":" + azurite.getMappedPort(BLOB_PORT) + "/devstoreaccount1";
    }

    @Override
    protected BlobStore createBlobStore() {
        AzureProviderConfig config = AzureProviderConfig.builder().name("test-azure").storageAccount("devstoreaccount1")
                .storageEndpoint(storageEndpoint()).noCredentials(true)
                .enable(io.foundry.aether.core.storage.BlobStore.class).build();
        AzureCloudProvider provider = new AzureCloudProvider(config);
        provider.initialize();
        return new AzureBlobStore(provider);
    }

    @Override
    protected void cleanUp() {
        BlobServiceClient client = new BlobServiceClientBuilder()
                .connectionString("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
                        + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
                        + "BlobEndpoint=" + storageEndpoint() + ";")
                .buildClient();
        client.listBlobContainers().forEach(c -> client.deleteBlobContainer(c.getName()));
    }
}
