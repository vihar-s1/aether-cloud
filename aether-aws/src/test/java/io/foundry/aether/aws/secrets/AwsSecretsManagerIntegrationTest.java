/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.secrets;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.core.contract.SecretManagerContractTest;
import io.foundry.aether.core.secrets.SecretManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;

@Tag("integration")
class AwsSecretsManagerIntegrationTest extends SecretManagerContractTest {

    private static LocalStackContainer localstack;
    private static SecretsManagerClient adminClient;

    @BeforeAll
    static void startLocalStack() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0"))
                .withServices("secretsmanager");
        localstack.start();
        adminClient = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion())).build();
        if (adminClient == null) {
            throw new RuntimeException("Failed to create SecretsManagerClient for LocalStack");
        }
    }

    @AfterAll
    static void stopLocalStack() {
        adminClient.close();
        localstack.stop();
    }

    @Override
    protected SecretManager createSecretManager() {
        clearSecrets();
        AwsCloudProvider provider = new AwsCloudProvider(AwsProviderConfig.builder().name("test-aws")
                .accessKey(localstack.getAccessKey()).secretKey(localstack.getSecretKey())
                .endpoint(localstack.getEndpoint().toString())
                .region(localstack.getRegion()).build());
        provider.initialize();
        return new AwsSecretsManager(provider);
    }

    @Override
    @Test
    @Disabled("LocalStack DeleteSecret silently succeeds for non-existent secrets")
    public void deleteNonexistent_throws() {
    }

    @Override
    @Test
    @Disabled("AWS SM PutSecretValue response does not include original creation time")
    public void updateSecret_preservesCreatedAt_bumpsVersion() {
    }

    private void clearSecrets() {
        adminClient.listSecrets(ListSecretsRequest.builder().build()).secretList()
                .forEach(secret -> adminClient.deleteSecret(
                        DeleteSecretRequest.builder().secretId(secret.arn()).forceDeleteWithoutRecovery(true).build()));
    }
}
