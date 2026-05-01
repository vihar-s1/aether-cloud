/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.compute;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.internal.AzureUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceInfo;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AzureComputeEngine implements ComputeEngine {

    private final AzureCloudProvider provider;
    private final ComputeManager computeManager;
    private final String resourceGroup;

    public AzureComputeEngine(AzureCloudProvider provider) {
        this.provider = provider;
        this.computeManager = provider.computeManager();
        this.resourceGroup = provider.config().resourceGroup()
                .orElseThrow(() -> new InvalidConfigurationException(AzureCloudProvider.PROVIDER_NAME, "init",
                        "'resource-group' is required for compute"));
    }

    AzureComputeEngine(AzureCloudProvider provider, ComputeManager computeManager) {
        this.provider = provider;
        this.resourceGroup = provider.config().resourceGroup()
                .orElseThrow(() -> new InvalidConfigurationException(AzureCloudProvider.PROVIDER_NAME, "init",
                        "'resource-group' is required for compute"));
        this.computeManager = computeManager;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public InstanceInfo createInstance(InstanceConfig config) {
        if (config.adminUser() == null || config.adminUser().isBlank()) {
            throw new InvalidConfigurationException(AzureCloudProvider.PROVIDER_NAME, "createInstance",
                    "'adminUser' is required for Azure VM creation");
        }
        if (config.sshPublicKey() == null || config.sshPublicKey().isBlank()) {
            throw new InvalidConfigurationException(AzureCloudProvider.PROVIDER_NAME, "createInstance",
                    "'sshPublicKey' is required for Azure VM creation");
        }
        try {
            VirtualMachine vm = computeManager.virtualMachines().define(config.name()).withRegion(config.region())
                    .withExistingResourceGroup(resourceGroup).withNewPrimaryNetwork("10.0.0.0/28")
                    .withPrimaryPrivateIPAddressDynamic().withoutPrimaryPublicIPAddress()
                    .withPopularLinuxImage(
                            com.azure.resourcemanager.compute.models.KnownLinuxVirtualMachineImage.UBUNTU_SERVER_20_04_LTS)
                    .withRootUsername(config.adminUser()).withSsh(config.sshPublicKey())
                    .withSize(VirtualMachineSizeTypes.fromString(config.instanceType())).withTags(config.tags())
                    .create();
            return _vmToInfo(vm);
        } catch (InvalidConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "createInstance", INSTANCE, config.name());
        }
    }

    @Override
    public void terminateInstance(String instanceId) {
        try {
            VirtualMachine vm = _findById(instanceId);
            computeManager.virtualMachines().deleteById(vm.id());
        } catch (io.foundry.aether.core.exception.ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "terminateInstance", INSTANCE, instanceId);
        }
    }

    @Override
    public InstanceInfo getInstance(String instanceId) {
        return _vmToInfo(_findById(instanceId));
    }

    @Override
    public ListResponse<InstanceInfo> listInstances(ListRequest<InstanceInfo> request) {
        try {
            List<InstanceInfo> all = new ArrayList<>();
            computeManager.virtualMachines().listByResourceGroup(resourceGroup).forEach(vm -> all.add(_vmToInfo(vm)));

            int total = all.size();
            int start = request.offset() != null ? Math.max(0, Math.min(request.offset(), total)) : 0;
            int end = request.limit() != null ? Math.min(start + request.limit(), total) : total;
            return new ListResponse<>(all.subList(start, end), null, end < total);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "listInstances", INSTANCE, null);
        }
    }

    private VirtualMachine _findById(String instanceId) {
        try {
            if (instanceId.startsWith("/")) {
                VirtualMachine vm = computeManager.virtualMachines().getById(instanceId);
                if (vm == null) {
                    throw new io.foundry.aether.core.exception.ResourceNotFoundException(
                            AzureCloudProvider.PROVIDER_NAME, "getInstance", INSTANCE, instanceId, null);
                }
                return vm;
            }
            VirtualMachine vm = computeManager.virtualMachines().getByResourceGroup(resourceGroup, instanceId);
            if (vm == null) {
                throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                        "getInstance", INSTANCE, instanceId, null);
            }
            return vm;
        } catch (io.foundry.aether.core.exception.ResourceNotFoundException e) {
            throw e;
        } catch (ResourceNotFoundException e) {
            throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                    "getInstance", INSTANCE, instanceId, e);
        } catch (Exception e) {
            throw AzureUtils.wrapAzureException(e, "getInstance", INSTANCE, instanceId);
        }
    }

    private InstanceInfo _vmToInfo(VirtualMachine vm) {
        Map<String, String> tags = vm.tags() != null ? new HashMap<>(vm.tags()) : Map.of();
        String publicIp = null;
        String privateIp = null;
        var nic = vm.getPrimaryNetworkInterface();
        if (nic != null) {
            privateIp = nic.primaryPrivateIP();
            var publicIpAddr = nic.primaryIPConfiguration().getPublicIpAddress();
            if (publicIpAddr != null) {
                publicIp = publicIpAddr.ipAddress();
            }
        }
        return new InstanceInfo(vm.id(), vm.name(), _mapPowerState(vm), publicIp, privateIp, 0L, tags);
    }

    private InstanceState _mapPowerState(VirtualMachine vm) {
        if (vm.powerState() == null)
            return InstanceState.UNKNOWN;
        String state = vm.powerState().toString().toLowerCase();
        if (state.contains("running"))
            return InstanceState.RUNNING;
        if (state.contains("starting"))
            return InstanceState.PENDING;
        if (state.contains("deallocating"))
            return InstanceState.DEALLOCATING;
        if (state.contains("deallocated"))
            return InstanceState.DEALLOCATED;
        if (state.contains("stopping"))
            return InstanceState.STOPPING;
        if (state.contains("stopped"))
            return InstanceState.STOPPED;
        return InstanceState.UNKNOWN;
    }
}
