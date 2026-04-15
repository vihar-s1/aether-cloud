/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

import java.util.Map;

public record InstanceConfig(
        String name, String instanceType, String imageId, String region, Map<String, String> tags) {}
