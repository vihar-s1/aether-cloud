/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.compute;

public enum InstanceState {
    PENDING, RUNNING, STOPPING, STOPPED, DEALLOCATING, DEALLOCATED, SUSPENDING, SUSPENDED, TERMINATED, UNKNOWN
}
