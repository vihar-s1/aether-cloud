/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.compute;

import io.foundry.aether.core.compute.ComputeEngine;
import io.foundry.aether.core.contract.ComputeEngineContractTest;
import io.foundry.aether.inmemory.InMemoryCloudProvider;

class InMemoryComputeEngineContractTest extends ComputeEngineContractTest {

    @Override
    protected ComputeEngine createComputeEngine() {
        return new InMemoryComputeEngine(new InMemoryCloudProvider());
    }
}
