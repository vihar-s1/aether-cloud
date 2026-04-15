/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import java.time.Instant;
import java.util.Map;

public record InstanceInfo(
        String instanceId,
        String name,
        InstanceState state,
        String publicIp,
        String privateIp,
        Instant launchTime,
        Map<String, String> tags) {}
