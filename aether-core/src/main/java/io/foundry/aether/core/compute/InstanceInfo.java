/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import java.util.Map;

public record InstanceInfo(
        String instanceId,
        String name,
        InstanceState state,
        String publicIp,
        String privateIp,
        long launchTimeMs,
        Map<String, String> tags) {

    public InstanceInfo withState(InstanceState newState) {
        return new InstanceInfo(instanceId, name, newState, publicIp, privateIp, launchTimeMs, tags);
    }
}
