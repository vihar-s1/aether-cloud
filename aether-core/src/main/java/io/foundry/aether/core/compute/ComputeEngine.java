/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import io.foundry.aether.core.CloudService;
import java.util.List;

public interface ComputeEngine extends CloudService {

    InstanceInfo createInstance(InstanceConfig config);

    void terminateInstance(String instanceId);

    InstanceInfo getInstance(String instanceId);

    List<InstanceInfo> listInstances();
}
