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
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
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
                .withServices(Service.SECRETSMANAGER);
        localstack.start();
        adminClient = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(Service.SECRETSMANAGER))
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
                .endpoint(localstack.getEndpointOverride(Service.SECRETSMANAGER).toString())
                .region(localstack.getRegion()).build());
        provider.initialize();
        return new AwsSecretsManager(provider);
    }

    private void clearSecrets() {
        adminClient.listSecrets(ListSecretsRequest.builder().build()).secretList()
                .forEach(secret -> adminClient.deleteSecret(
                        DeleteSecretRequest.builder().secretId(secret.arn()).forceDeleteWithoutRecovery(true).build()));
    }
}
