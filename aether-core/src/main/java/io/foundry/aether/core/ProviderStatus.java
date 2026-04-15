/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

public enum ProviderStatus {
    INITIALIZED,
    RUNNING,
    DEGRADED,
    SHUTDOWN;

    public boolean isAvailable() {
        return this == INITIALIZED || this == RUNNING;
    }
}
