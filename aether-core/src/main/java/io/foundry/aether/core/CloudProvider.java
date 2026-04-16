/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

public interface CloudProvider {

    String name();

    ProviderStatus status();

    void initialize();

    void shutdown();
}
