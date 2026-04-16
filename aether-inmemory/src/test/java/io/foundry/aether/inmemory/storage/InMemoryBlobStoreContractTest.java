/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.storage;

import io.foundry.aether.core.contract.BlobStoreContractTest;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.inmemory.InMemoryCloudProvider;

class InMemoryBlobStoreContractTest extends BlobStoreContractTest {

    @Override
    protected BlobStore createBlobStore() {
        return new InMemoryBlobStore(new InMemoryCloudProvider());
    }
}
