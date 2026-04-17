/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.storage;

import static io.foundry.aether.nfs.internal.NSFUtils.toPath;
import static io.foundry.aether.nfs.internal.NSFUtils.wrapIOException;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.*;
import io.foundry.aether.core.internal.CollectionUtils;
import io.foundry.aether.core.internal.FileUtils;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class NFSBlobStore implements BlobStore {

    private final NFSCloudProvider provider;

    public NFSBlobStore(NFSCloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        try (InputStream inputStream = request.data()) {
            Path path = FileUtils.ensurePathExists(provider.basePath(), request.bucket(), request.key());
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            return new BlobMetadata(
                    request.bucket(), request.key(), Files.size(path), request.contentType(), Instant.now(), Map.of());
        } catch (IOException e) {
            throw wrapIOException(e, "upload", new BlobRef(request.bucket(), request.key()));
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        try {
            Path path = toPath(provider, ref);
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("nfs", "download", BlobStore.BLOB, ref.getId());
            }
            BlobMetadata metadata = new BlobMetadata(
                    ref.bucket(), ref.key(), Files.size(path), Files.probeContentType(path), Instant.now(), Map.of());
            return new BlobContent(Files.newInputStream(path), metadata);
        } catch (IOException e) {
            throw wrapIOException(e, "download", ref);
        }
    }

    @Override
    public List<BlobMetadata> list(ListBlobsRequest request) {
        Path path = Path.of(provider.basePath()).resolve(request.bucket());
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<Path> files = FileUtils.filterFiles(path, p -> {
                String relativePath = path.relativize(p).toString();
                return relativePath.startsWith(request.prefix());
            });
            return CollectionUtils.transformList(files, p -> {
                String key = path.relativize(p).toString();
                try {
                    return new BlobMetadata(
                            request.bucket(), key, Files.size(p), Files.probeContentType(p), Instant.now(), Map.of());
                } catch (IOException e) {
                    throw wrapIOException(e, "list", new BlobRef(request.bucket(), key));
                }
            });
        } catch (IOException e) {
            throw wrapIOException(e, "list", new BlobRef(request.bucket(), request.prefix()));
        }
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            Path path = toPath(provider, ref);
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("nfs", "delete", BlobStore.BLOB, ref.getId());
            }
            Files.delete(path);
        } catch (IOException e) {
            throw wrapIOException(e, "delete", ref);
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        return Files.exists(toPath(provider, ref));
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            Path path = toPath(provider, ref);
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("nfs", "getMetadata", BlobStore.BLOB, ref.getId());
            }
            return new BlobMetadata(
                    ref.bucket(), ref.key(), Files.size(path), Files.probeContentType(path), Instant.now(), Map.of());
        } catch (IOException e) {
            throw wrapIOException(e, "getMetadata", ref);
        }
    }
}
