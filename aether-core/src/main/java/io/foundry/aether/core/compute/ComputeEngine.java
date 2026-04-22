/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import java.util.List;

public interface ComputeEngine extends CloudService {

    String INSTANCE = "instance";

    default String serviceName() {
        return "compute-engine";
    }

    /**
     * Submits a request to create a new instance and returns immediately with the
     * initial {@link InstanceState#PENDING} state. The IP address fields will be
     * {@code null} until the instance reaches {@link InstanceState#RUNNING}. Use
     * {@link #getInstance(String)} to poll for the current state.
     */
    InstanceInfo createInstance(InstanceConfig config);

    /**
     * Initiates termination of an instance. Returns immediately; the instance
     * transitions through {@link InstanceState#STOPPING} to
     * {@link InstanceState#TERMINATED} asynchronously.
     *
     * @throws ResourceNotFoundException
     *             if the instance does not exist
     */
    void terminateInstance(String instanceId);

    /**
     * Returns the current state of an instance.
     *
     * @throws ResourceNotFoundException
     *             if the instance does not exist
     */
    InstanceInfo getInstance(String instanceId);

    /**
     * Returns all instances regardless of state, including
     * {@link InstanceState#TERMINATED} instances.
     */
    List<InstanceInfo> listInstances();
}
