/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.compute;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.compute.v1.AttachedDisk;
import com.google.cloud.compute.v1.AttachedDiskInitializeParams;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InsertInstanceRequest;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworkInterface;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceInfo;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.OperationNotSupportedException;
import io.foundry.aether.gcp.GcpCloudProvider;
import io.foundry.aether.gcp.internal.GcpUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class GcpComputeEngine implements ComputeEngine {

    private final GcpCloudProvider provider;
    private final InstancesClient instancesClient;
    private final String projectId;
    private final String zone;

    public GcpComputeEngine(GcpCloudProvider provider) {
        this.provider = provider;
        this.instancesClient = provider.instancesClient();
        this.projectId = provider.config().projectId();
        this.zone = provider.config().zone()
                .orElseThrow(() -> new OperationNotSupportedException(GcpCloudProvider.PROVIDER_NAME, "compute",
                        "Compute operations require 'zone' to be configured in GcpProviderConfig"));
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public InstanceInfo createInstance(InstanceConfig config) {
        Instance instanceResource = Instance.newBuilder().setName(config.name())
                .setMachineType("zones/" + zone + "/machineTypes/" + config.instanceType())
                .addDisks(AttachedDisk.newBuilder().setBoot(true).setAutoDelete(true)
                        .setInitializeParams(
                                AttachedDiskInitializeParams.newBuilder().setSourceImage(config.imageId()).build())
                        .build())
                .addNetworkInterfaces(NetworkInterface.newBuilder().setName("global/networks/default").build())
                .putAllLabels(config.tags() != null ? config.tags() : Map.of()).build();
        try {
            InsertInstanceRequest request = InsertInstanceRequest.newBuilder().setProject(projectId).setZone(zone)
                    .setInstanceResource(instanceResource).build();
            instancesClient.insertAsync(request).get();
            return getInstance(config.name());
        } catch (ExecutionException e) {
            Exception cause = e.getCause() instanceof Exception ex ? ex : e;
            throw GcpUtils.wrapGcpException(cause, "createInstance", INSTANCE, config.name(),
                    CloudErrorCodes.COMPUTE_NOT_FOUND);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, "createInstance", CloudErrorCodes.UNKNOWN,
                    "Interrupted while waiting for instance to start", e);
        }
    }

    @Override
    public void terminateInstance(String instanceId) {
        try {
            instancesClient.deleteAsync(projectId, zone, instanceId).get();
        } catch (ExecutionException e) {
            Exception cause = e.getCause() instanceof Exception ex ? ex : e;
            throw GcpUtils.wrapGcpException(cause, "terminateInstance", INSTANCE, instanceId,
                    CloudErrorCodes.COMPUTE_NOT_FOUND);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, "terminateInstance", CloudErrorCodes.UNKNOWN,
                    "Interrupted while waiting for instance to terminate", e);
        }
    }

    @Override
    public InstanceInfo getInstance(String instanceId) {
        try {
            Instance instance = instancesClient.get(projectId, zone, instanceId);
            return _instanceToInfo(instance);
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "getInstance", INSTANCE, instanceId, CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
    }

    @Override
    public ListResponse<InstanceInfo> listInstances(ListRequest<InstanceInfo> request) {
        try {
            var gcpRequest = com.google.cloud.compute.v1.ListInstancesRequest.newBuilder().setProject(projectId)
                    .setZone(zone);
            if (request.cursor() != null) {
                gcpRequest.setPageToken(request.cursor());
            }
            if (request.limit() != null) {
                gcpRequest.setMaxResults(request.limit());
            }
            var page = instancesClient.list(gcpRequest.build()).getPage();
            List<InstanceInfo> instances = new ArrayList<>();
            for (Instance instance : page.getValues()) {
                instances.add(_instanceToInfo(instance));
            }
            String nextCursor = page.getNextPageToken();
            nextCursor = (nextCursor != null && !nextCursor.isEmpty()) ? nextCursor : null;
            return new ListResponse<>(instances, nextCursor, nextCursor != null);
        } catch (ApiException e) {
            throw GcpUtils.wrapGcpException(e, "listInstances", INSTANCE, null, CloudErrorCodes.COMPUTE_NOT_FOUND);
        }
    }

    private InstanceInfo _instanceToInfo(Instance instance) {
        String publicIp = null;
        String privateIp = null;
        if (!instance.getNetworkInterfacesList().isEmpty()) {
            NetworkInterface ni = instance.getNetworkInterfacesList().get(0);
            privateIp = ni.getNetworkIP();
            if (!ni.getAccessConfigsList().isEmpty()) {
                publicIp = ni.getAccessConfigsList().get(0).getNatIP();
                if (publicIp != null && publicIp.isEmpty()) {
                    publicIp = null;
                }
            }
        }
        Map<String, String> tags = new HashMap<>(instance.getLabelsMap());
        long createdAt = _parseTimestamp(instance.getCreationTimestamp());
        return new InstanceInfo(String.valueOf(instance.getId()), instance.getName(),
                _mapInstanceState(instance.getStatus()), publicIp, privateIp, createdAt, tags);
    }

    private InstanceState _mapInstanceState(String gcpStatus) {
        if (gcpStatus == null) {
            return InstanceState.UNKNOWN;
        }
        return switch (gcpStatus.toUpperCase()) {
            case "PROVISIONING", "STAGING" -> InstanceState.PENDING;
            case "RUNNING" -> InstanceState.RUNNING;
            case "STOPPING", "SUSPENDING" -> InstanceState.STOPPING;
            case "STOPPED", "SUSPENDED" -> InstanceState.STOPPED;
            case "TERMINATED" -> InstanceState.TERMINATED;
            default -> InstanceState.UNKNOWN;
        };
    }

    private long _parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(ts).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
