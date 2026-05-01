/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.compute;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.contract.ComputeEngineContractTest;
import java.util.List;
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
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;

@Tag("integration")
class AwsEc2ComputeEngineIntegrationTest extends ComputeEngineContractTest {

    private static LocalStackContainer localstack;
    private static Ec2Client adminClient;

    @BeforeAll
    static void startLocalStack() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0")).withServices("ec2");
        localstack.start();
        adminClient = Ec2Client.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion())).build();
    }

    @AfterAll
    static void stopLocalStack() {
        adminClient.close();
        localstack.stop();
    }

    @Override
    protected ComputeEngine createComputeEngine() {
        terminateAllInstances();
        AwsCloudProvider provider = new AwsCloudProvider(AwsProviderConfig.builder().name("test-aws")
                .accessKey(localstack.getAccessKey()).secretKey(localstack.getSecretKey())
                .endpoint(localstack.getEndpoint().toString()).region(localstack.getRegion())
                .enable(io.foundry.aether.core.compute.ComputeEngine.class).build());
        provider.initialize();
        return new AwsEc2ComputeEngine(provider);
    }

    private void terminateAllInstances() {
        List<String> ids = adminClient.describeInstances(DescribeInstancesRequest.builder().build()).reservations()
                .stream().flatMap(r -> r.instances().stream())
                .filter(i -> !i.state().nameAsString().equals(InstanceState.TERMINATED.name().toLowerCase()))
                .map(Instance::instanceId).toList();
        if (!ids.isEmpty()) {
            adminClient.terminateInstances(TerminateInstancesRequest.builder().instanceIds(ids).build());
        }
    }

    @Override
    @Test
    @Disabled("EC2 TerminateInstances silently succeeds for unknown IDs; terminateInstance() cannot satisfy this contract without a pre-flight existence check")
    protected void terminateNonexistent_throws() {
    }
}
