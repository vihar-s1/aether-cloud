/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.compute;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceInfo;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;

public class AwsEc2ComputeEngine implements ComputeEngine {

    private final AwsCloudProvider provider;
    private final Ec2Client ec2Client;

    public AwsEc2ComputeEngine(AwsCloudProvider provider) {
        this.provider = provider;
        ec2Client = Ec2Client.builder()
                .region(Region.of(provider.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey(), provider.secretKey())))
                .build();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public InstanceInfo createInstance(InstanceConfig config) {
        RunInstancesResponse response = ec2Client.runInstances(RunInstancesRequest.builder()
                .imageId(config.imageId())
                .instanceType(config.instanceType())
                .minCount(1)
                .maxCount(1)
                .tagSpecifications(_buildTagSpecifications(config.name(), config.tags()))
                .build());

        Instance instance = response.instances().getFirst();
        return _instanceToInfo(instance);
    }

    @Override
    public void terminateInstance(String instanceId) {
        ec2Client.terminateInstances(
                TerminateInstancesRequest.builder().instanceIds(instanceId).build());
    }

    @Override
    public InstanceInfo getInstance(String instanceId) {
        DescribeInstancesResponse response = ec2Client.describeInstances(
                DescribeInstancesRequest.builder().instanceIds(instanceId).build());

        if (response.reservations().isEmpty()
                || response.reservations().get(0).instances().isEmpty()) {
            throw new ResourceNotFoundException(provider.name(), "getInstance", "instance", instanceId);
        }

        Instance instance = response.reservations().get(0).instances().get(0);
        return _instanceToInfo(instance);
    }

    @Override
    public List<InstanceInfo> listInstances() {
        DescribeInstancesResponse response =
                ec2Client.describeInstances(DescribeInstancesRequest.builder().build());

        return response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .map(this::_instanceToInfo)
                .toList();
    }

    private TagSpecification _buildTagSpecifications(String name, java.util.Map<String, String> tags) {
        java.util.List<Tag> tagList = new java.util.ArrayList<>();
        tagList.add(Tag.builder().key("Name").value(name).build());

        if (tags != null) {
            tags.forEach((key, value) ->
                    tagList.add(Tag.builder().key(key).value(value).build()));
        }

        return TagSpecification.builder().resourceType("instance").tags(tagList).build();
    }

    private InstanceInfo _instanceToInfo(Instance instance) {
        String name = instance.tags().stream()
                .filter(tag -> "Name".equals(tag.key()))
                .map(Tag::value)
                .findFirst()
                .orElse(instance.instanceId());

        java.util.Map<String, String> tags = new java.util.HashMap<>();
        instance.tags().forEach(tag -> {
            if (!"Name".equals(tag.key())) {
                tags.put(tag.key(), tag.value());
            }
        });

        return new InstanceInfo(
                instance.instanceId(),
                name,
                _mapInstanceState(instance.state().nameAsString()),
                instance.publicIpAddress(),
                instance.privateIpAddress(),
                instance.launchTime() != null ? instance.launchTime().toEpochMilli() : 0L,
                tags);
    }

    private InstanceState _mapInstanceState(String awsState) {
        return switch (awsState.toLowerCase()) {
            case "pending" -> InstanceState.PENDING;
            case "running" -> InstanceState.RUNNING;
            case "stopping" -> InstanceState.STOPPING;
            case "stopped" -> InstanceState.STOPPED;
            case "terminated" -> InstanceState.TERMINATED;
            default -> InstanceState.UNKNOWN;
        };
    }
}
