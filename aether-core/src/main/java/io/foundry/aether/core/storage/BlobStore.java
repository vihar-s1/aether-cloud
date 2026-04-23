/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.ResourceNotFoundException;

public interface BlobStore extends CloudService {

    String BLOB = "blob";
    String BUCKET = "bucket";

    default String serviceName() {
        return "blob-store";
    }

    BlobMetadata upload(UploadBlobRequest request);

    /**
     * Downloads a blob and returns its content stream alongside metadata.
     *
     * @throws ResourceNotFoundException
     *             if the blob does not exist
     */
    BlobContent download(BlobRef ref);

    /**
     * Lists blobs in a bucket page by page.
     *
     * <p>
     * <b>Pagination support varies by provider</b> — not all providers support
     * both cursor-based and offset-based modes:
     * <ul>
     * <li>AWS S3: cursor-based only ({@link ListBlobsRequest#withCursor}); offset
     * is ignored.</li>
     * <li>NFS: offset-based only ({@link ListBlobsRequest#withOffset}); cursor is
     * ignored. {@link ListResponse#nextCursor()} is always {@code null}.</li>
     * <li>In-memory: both modes supported.</li>
     * </ul>
     *
     * <p>
     * Note: {@code contentType} and {@code metadata} fields may be {@code null} or
     * empty for cloud providers (AWS, GCP) because their list APIs do not return
     * per-object metadata. Use {@link #getMetadata(BlobRef)} to fetch full metadata
     * for a specific blob.
     */
    ListResponse<BlobMetadata> list(ListBlobsRequest request);

    /**
     * Deletes a blob. This operation is idempotent — deleting a blob that does not
     * exist silently succeeds.
     */
    void delete(BlobRef ref);

    boolean exists(BlobRef ref);

    /**
     * Returns metadata for a blob, including {@code contentType} and user-defined
     * metadata.
     *
     * @throws ResourceNotFoundException
     *             if the blob does not exist
     */
    BlobMetadata getMetadata(BlobRef ref);
}
