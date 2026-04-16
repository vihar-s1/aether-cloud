/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import io.foundry.aether.core.CloudService;
import java.util.List;

public interface BlobStore extends CloudService {

    BlobMetadata upload(UploadBlobRequest request);

    BlobContent download(BlobRef ref);

    List<BlobMetadata> list(ListBlobsRequest request);

    void delete(BlobRef ref);

    boolean exists(BlobRef ref);

    BlobMetadata getMetadata(BlobRef ref);
}
