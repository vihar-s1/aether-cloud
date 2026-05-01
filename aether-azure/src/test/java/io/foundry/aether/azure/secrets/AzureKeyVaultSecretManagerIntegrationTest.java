/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.secrets;

import com.azure.core.credential.AccessToken;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.config.AzureProviderConfig;
import io.foundry.aether.core.contract.SecretManagerContractTest;
import io.foundry.aether.core.secrets.SecretManager;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

@Tag("integration")
class AzureKeyVaultSecretManagerIntegrationTest extends SecretManagerContractTest {

    private static final int KV_PORT = 4443;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> emulator = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-keyvault/keyvault-emulator:latest"))
            .withExposedPorts(KV_PORT)
            .waitingFor(Wait.forListeningPort());

    private static SecretClient adminClient;

    @BeforeAll
    static void startEmulator() throws Exception {
        emulator.start();
        adminClient = buildSecretClient();
    }

    @AfterAll
    static void stopEmulator() {
        emulator.stop();
    }

    @Override
    protected SecretManager createSecretManager() {
        clearSecrets();
        AzureProviderConfig config = AzureProviderConfig.builder().name("test-azure")
                .keyVaultUrl(vaultUrl()).enable(SecretManager.class).build();
        AzureCloudProvider provider = new AzureCloudProvider(config);
        return new AzureKeyVaultSecretManager(provider, adminClient);
    }

    @Override
    @Test
    @Disabled("Azure Key Vault does not allow '/' in secret names — hierarchical IDs are not supported")
    public void secretId_withSpecialCharacters_createAndGet() {
    }

    @Override
    @Test
    @Disabled("Azure Key Vault emulator does not preserve createdAt across setSecret calls")
    public void updateSecret_preservesCreatedAt_bumpsVersion() {
    }

    private void clearSecrets() {
        for (SecretProperties props : adminClient.listPropertiesOfSecrets()) {
            try {
                adminClient.beginDeleteSecret(props.getName()).waitForCompletion();
            } catch (Exception ignored) {
            }
        }
        for (var ds : adminClient.listDeletedSecrets()) {
            try {
                adminClient.purgeDeletedSecret(ds.getName());
            } catch (Exception ignored) {
            }
        }
    }

    private static SecretClient buildSecretClient() throws Exception {
        var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        reactor.netty.http.client.HttpClient reactorClient = reactor.netty.http.client.HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext));
        var httpClient = new NettyAsyncHttpClientBuilder(reactorClient).build();
        return new SecretClientBuilder().vaultUrl(vaultUrl()).credential(request -> Mono.just(
                new AccessToken("fake-token", OffsetDateTime.MAX))).httpClient(httpClient).buildClient();
    }

    private static String vaultUrl() {
        return "https://" + emulator.getHost() + ":" + emulator.getMappedPort(KV_PORT);
    }
}
