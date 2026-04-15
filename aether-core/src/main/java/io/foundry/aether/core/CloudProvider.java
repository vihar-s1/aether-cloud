/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

import io.foundry.aether.core.exception.CloudException;

public interface CloudProvider {

    String name();

    ProviderStatus status();

    void initialize() throws CloudException;

    void shutdown() throws CloudException;
}
