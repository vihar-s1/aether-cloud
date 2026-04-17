/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.storage;

import io.foundry.aether.core.contract.BlobStoreContractTest;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

public class NFSBlobStoreContractTest extends BlobStoreContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected BlobStore createBlobStore() {
        return new NFSBlobStore(new NFSCloudProvider(tempDir.toString()));
    }
}
