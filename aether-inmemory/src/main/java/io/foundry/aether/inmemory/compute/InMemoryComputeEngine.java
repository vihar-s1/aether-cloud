/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.compute;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.compute.InstanceConfig;
import io.foundry.aether.core.compute.InstanceInfo;
import io.foundry.aether.core.compute.InstanceState;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class InMemoryComputeEngine implements ComputeEngine {

    private final CloudProvider provider;
    private final ConcurrentHashMap<String, InstanceInfo> instances = new ConcurrentHashMap<>();
    private final AtomicInteger ipCounter = new AtomicInteger(0);

    public InMemoryComputeEngine(CloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public InstanceInfo createInstance(InstanceConfig config) {
        String instanceId = UUID.randomUUID().toString();
        var info = new InstanceInfo(instanceId, config.name(), InstanceState.RUNNING, null,
                "10.0.0." + ipCounter.incrementAndGet(), System.currentTimeMillis(), config.tags());
        instances.put(instanceId, info);
        return info;
    }

    @Override
    public void terminateInstance(String instanceId) {
        InstanceInfo existing = instances.get(instanceId);
        if (existing == null) {
            throw new ResourceNotFoundException(provider.name(), "terminateInstance", "instance", instanceId);
        }
        instances.put(instanceId, existing.withState(InstanceState.TERMINATED));
    }

    @Override
    public InstanceInfo getInstance(String instanceId) {
        InstanceInfo info = instances.get(instanceId);
        if (info == null) {
            throw new ResourceNotFoundException(provider.name(), "getInstance", "instance", instanceId);
        }
        return info;
    }

    @Override
    public List<InstanceInfo> listInstances() {
        return List.copyOf(instances.values());
    }
}
