/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.secrets;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import io.foundry.aether.core.contract.SecretManagerContractTest;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.gcp.GcpCloudProvider;
import io.foundry.aether.gcp.config.GcpProviderConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
class GcpSecretManagerIntegrationTest extends SecretManagerContractTest {

    private static final int GRPC_PORT = 9090;
    private static final String PROJECT_ID = "test-project";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> emulator = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/blackwell-systems/gcp-secret-manager-emulator:latest"))
            .withExposedPorts(GRPC_PORT).waitingFor(Wait.forListeningPort());

    private static SecretManagerServiceClient adminClient;

    @BeforeAll
    static void startEmulator() throws Exception {
        emulator.start();
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(emulator.getHost() + ":" + emulator.getMappedPort(GRPC_PORT)).usePlaintext().build();
        adminClient = SecretManagerServiceClient.create(SecretManagerServiceSettings.newBuilder()
                .setTransportChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create()).build());
    }

    @AfterAll
    static void stopEmulator() {
        adminClient.close();
        emulator.stop();
    }

    @Override
    protected SecretManager createSecretManager() {
        clearSecrets();
        GcpProviderConfig config = GcpProviderConfig.builder().name("test-gcp").projectId(PROJECT_ID)
                .secretManagerEndpoint(emulator.getHost() + ":" + emulator.getMappedPort(GRPC_PORT)).noCredentials(true)
                .enable(io.foundry.aether.core.secrets.SecretManager.class).build();
        GcpCloudProvider provider = new GcpCloudProvider(config);
        provider.initialize();
        return new GcpSecretManager(provider);
    }

    @Override
    @Test
    @Disabled("GCP Secret Manager does not allow '/' in secret names — hierarchical IDs are not supported")
    public void secretId_withSpecialCharacters_createAndGet() {
    }

    @Override
    @Test
    @Disabled("GCP Secret Manager emulator does not persist createdAt across addSecretVersion calls")
    public void updateSecret_preservesCreatedAt_bumpsVersion() {
    }

    private void clearSecrets() {
        for (var secret : adminClient.listSecrets(ProjectName.of(PROJECT_ID)).iterateAll()) {
            adminClient.deleteSecret(SecretName.parse(secret.getName()));
        }
    }
}
