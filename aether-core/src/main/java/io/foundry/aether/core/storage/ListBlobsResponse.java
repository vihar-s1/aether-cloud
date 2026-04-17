/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import java.util.List;

public record ListBlobsResponse(List<BlobMetadata> blobs, String nextCursor, boolean hasMore) {

    public static ListBlobsResponse empty() {
        return new ListBlobsResponse(List.of(), null, false);
    }
}
