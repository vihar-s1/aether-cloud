/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.compute;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceInfo;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AwsEc2ComputeEngine implements ComputeEngine {

    private final AwsCloudProvider provider;
    private final Ec2Client ec2Client;

    public AwsEc2ComputeEngine(AwsCloudProvider provider) {
        this.provider = provider;
        this.ec2Client = provider.ec2Client();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public InstanceInfo createInstance(InstanceConfig config) {
        try {
            RunInstancesResponse response = ec2Client.runInstances(RunInstancesRequest.builder()
                    .imageId(config.imageId()).instanceType(config.instanceType()).minCount(1).maxCount(1)
                    .tagSpecifications(_buildTagSpecifications(config.name(), config.tags())).build());
            return _instanceToInfo(response.instances().getFirst());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "createInstance", INSTANCE, config.name(),
                    CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
    }

    @Override
    public void terminateInstance(String instanceId) {
        try {
            ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instanceId).build());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "terminateInstance", INSTANCE, instanceId,
                    CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
    }

    @Override
    public InstanceInfo getInstance(String instanceId) {
        DescribeInstancesResponse response;
        try {
            response = ec2Client.describeInstances(DescribeInstancesRequest.builder().instanceIds(instanceId).build());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "getInstance", INSTANCE, instanceId, CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
        if (response.reservations().isEmpty() || response.reservations().get(0).instances().isEmpty()) {
            throw new ResourceNotFoundException(provider.name(), "getInstance", INSTANCE, instanceId, null,
                    CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
        return _instanceToInfo(response.reservations().get(0).instances().get(0));
    }

    @Override
    public List<InstanceInfo> listInstances() {
        try {
            DescribeInstancesResponse response = ec2Client
                    .describeInstances(DescribeInstancesRequest.builder().build());
            return response.reservations().stream().flatMap(reservation -> reservation.instances().stream())
                    .map(this::_instanceToInfo).toList();
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "listInstances", INSTANCE, null, CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
    }

    private TagSpecification _buildTagSpecifications(String name, Map<String, String> tags) {
        List<Tag> tagList = new ArrayList<>();
        tagList.add(Tag.builder().key("Name").value(name).build());
        if (tags != null) {
            tags.forEach((key, value) -> tagList.add(Tag.builder().key(key).value(value).build()));
        }
        return TagSpecification.builder().resourceType("instance").tags(tagList).build();
    }

    private InstanceInfo _instanceToInfo(Instance instance) {
        String name = instance.tags().stream().filter(tag -> "Name".equals(tag.key())).map(Tag::value).findFirst()
                .orElse(instance.instanceId());

        Map<String, String> tags = new HashMap<>();
        instance.tags().forEach(tag -> {
            if (!"Name".equals(tag.key())) {
                tags.put(tag.key(), tag.value());
            }
        });

        return new InstanceInfo(instance.instanceId(), name, _mapInstanceState(instance.state().nameAsString()),
                instance.publicIpAddress(), instance.privateIpAddress(),
                instance.launchTime() != null ? instance.launchTime().toEpochMilli() : 0L, tags);
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
