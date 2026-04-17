/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.storage;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.storage.*;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBlobStore implements BlobStore {

    private record StoredBlob(byte[] data, BlobMetadata metadata) {}

    private final InMemoryCloudProvider provider;
    private final ConcurrentHashMap<BlobRef, StoredBlob> store = new ConcurrentHashMap<>();

    public InMemoryBlobStore(InMemoryCloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        try {
            byte[] bytes = request.data().readAllBytes();
            var metadata = new BlobMetadata(
                    request.bucket(),
                    request.key(),
                    bytes.length,
                    request.contentType(),
                    System.currentTimeMillis(),
                    Map.of());
            store.put(new BlobRef(request.bucket(), request.key()), new StoredBlob(bytes, metadata));
            return metadata;
        } catch (IOException e) {
            throw new ProviderUnavailableException(provider.name(), "upload", "Failed to read input stream", e);
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        StoredBlob blob = store.get(ref);
        if (blob == null) {
            throw new ResourceNotFoundException(provider.name(), "download", BlobStore.BLOB, ref.getId());
        }
        return new BlobContent(new ByteArrayInputStream(blob.data()), blob.metadata());
    }

    @Override
    public ListBlobsResponse list(ListBlobsRequest request) {
        List<BlobMetadata> blobs = store.entrySet().stream()
                .filter(e -> e.getKey().bucket().equals(request.bucket())
                        && e.getKey().key().startsWith(request.prefix()))
                .map(e -> e.getValue().metadata())
                .toList();
        return new ListBlobsResponse(blobs, null, false);
    }

    @Override
    public BlobMetadata delete(BlobRef ref) {
        StoredBlob blob = store.remove(ref);
        return blob == null ? null : blob.metadata();
    }

    @Override
    public boolean exists(BlobRef ref) {
        return store.containsKey(ref);
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        StoredBlob blob = store.get(ref);
        if (blob == null) {
            throw new ResourceNotFoundException(provider.name(), "getMetadata", BlobStore.BLOB, ref.getId());
        }
        return blob.metadata();
    }
}
