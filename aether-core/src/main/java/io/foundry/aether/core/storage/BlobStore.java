/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import io.foundry.aether.core.CloudService;
import io.foundry.aether.core.exception.CloudException;
import java.util.List;

public interface BlobStore extends CloudService {

    BlobMetadata upload(UploadBlobRequest request) throws CloudException;

    BlobContent download(BlobRef ref) throws CloudException;

    List<BlobMetadata> list(ListBlobsRequest request) throws CloudException;

    void delete(BlobRef ref) throws CloudException;

    boolean exists(BlobRef ref) throws CloudException;

    BlobMetadata getMetadata(BlobRef ref) throws CloudException;
}
