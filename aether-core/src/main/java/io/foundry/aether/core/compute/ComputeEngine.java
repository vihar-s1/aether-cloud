/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.CloudException;
import java.util.List;

public interface ComputeEngine extends CloudService {

    InstanceInfo createInstance(InstanceConfig config) throws CloudException;

    void terminateInstance(String instanceId) throws CloudException;

    InstanceInfo getInstance(String instanceId) throws CloudException;

    List<InstanceInfo> listInstances() throws CloudException;
}
