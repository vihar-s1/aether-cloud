/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.storage;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBlobStore implements BlobStore {

    private record StoredBlob(byte[] data, BlobMetadata metadata) {}

    private final CloudProvider provider;
    private final ConcurrentHashMap<BlobRef, StoredBlob> store = new ConcurrentHashMap<>();

    public InMemoryBlobStore(CloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public String serviceName() {
        return "blob-store";
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        try {
            byte[] bytes = request.data().readAllBytes();
            var metadata = new BlobMetadata(
                    request.bucket(), request.key(), bytes.length, request.contentType(), Instant.now(), Map.of());
            store.put(new BlobRef(request.bucket(), request.key()), new StoredBlob(bytes, metadata));
            return metadata;
        } catch (IOException e) {
            throw new ProviderUnavailableException("inmemory", "upload", "Failed to read input stream", e);
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        StoredBlob blob = store.get(ref);
        if (blob == null) {
            throw new ResourceNotFoundException(
                    "inmemory", "download", ref.bucket() + "/" + ref.key(), "Blob not found");
        }
        return new BlobContent(new ByteArrayInputStream(blob.data()), blob.metadata());
    }

    @Override
    public List<BlobMetadata> list(ListBlobsRequest request) {
        return store.entrySet().stream()
                .filter(e -> e.getKey().bucket().equals(request.bucket())
                        && e.getKey().key().startsWith(request.prefix()))
                .map(e -> e.getValue().metadata())
                .toList();
    }

    @Override
    public void delete(BlobRef ref) {
        store.remove(ref);
    }

    @Override
    public boolean exists(BlobRef ref) {
        return store.containsKey(ref);
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        StoredBlob blob = store.get(ref);
        if (blob == null) {
            throw new ResourceNotFoundException(
                    "inmemory", "getMetadata", ref.bucket() + "/" + ref.key(), "Blob not found");
        }
        return blob.metadata();
    }
}
