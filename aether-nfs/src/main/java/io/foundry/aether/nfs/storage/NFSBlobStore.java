/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.storage;

import static io.foundry.aether.nfs.internal.NFSUtils.toPath;
import static io.foundry.aether.nfs.internal.NFSUtils.wrapIOException;

import com.google.common.util.concurrent.Striped;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.*;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Each write uses a temp-file-then-atomic-rename strategy so readers always
 * see a complete file. Per-key write locks (via a 64-stripe {@link Striped})
 * prevent concurrent uploads or deletes from racing on the same key. Reads do
 * not acquire locks — atomic writes ensure they observe either the complete old
 * or complete new content.
 */
@ThreadSafe
public class NFSBlobStore implements BlobStore {

    private final NFSCloudProvider provider;
    private final Striped<ReadWriteLock> locks = Striped.readWriteLock(64);

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
            Lock lock = locks.get(path.toString()).writeLock();
            lock.lock();
            try {
                _atomicWrite(path, inputStream);
                return new BlobMetadata(request.bucket(), request.key(), Files.size(path), request.contentType(),
                        Files.getLastModifiedTime(path).toMillis(), Map.of());
            } finally {
                lock.unlock();
            }
        } catch (IOException e) {
            throw wrapIOException(e, "upload", new BlobRef(request.bucket(), request.key()));
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        try {
            Path path = toPath(provider, ref);
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException(provider.name(), "download", BlobStore.BLOB, ref.getId());
            }
            BlobMetadata metadata = new BlobMetadata(ref.bucket(), ref.key(), Files.size(path),
                    Files.probeContentType(path), Files.getLastModifiedTime(path).toMillis(), Map.of());
            return new BlobContent(Files.newInputStream(path), metadata);
        } catch (IOException e) {
            throw wrapIOException(e, "download", ref);
        }
    }

    /**
     * Offset-based pagination only — cursor is ignored and {@link ListResponse#nextCursor()}
     * is always {@code null}. The directory is walked in full to sort paths, but
     * file metadata (size, mtime, content-type) is read only for the requested page.
     */
    @Override
    public ListResponse<BlobMetadata> list(ListBlobsRequest request) {
        Path bucketPath = Path.of(provider.basePath()).resolve(request.bucket());
        if (!Files.exists(bucketPath)) {
            return ListResponse.empty();
        }
        int offset = request.offset() != null ? request.offset() : 0;
        try (Stream<Path> walk = Files.walk(bucketPath)) {
            Stream<Path> filtered = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String rel = bucketPath.relativize(p).toString();
                        return !rel.startsWith(".tmp-")
                                && (request.prefix() == null || rel.startsWith(request.prefix()));
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .skip(offset);

            List<Path> page;
            boolean hasMore;
            if (request.limit() != null) {
                page = filtered.limit(request.limit() + 1L).toList();
                hasMore = page.size() > request.limit();
                if (hasMore) page = page.subList(0, request.limit());
            } else {
                page = filtered.toList();
                hasMore = false;
            }

            List<BlobMetadata> items = page.stream()
                    .map(p -> _toMetadata(request.bucket(), bucketPath, p))
                    .toList();
            return new ListResponse<>(items, null, hasMore);
        } catch (IOException e) {
            throw wrapIOException(e, "list", new BlobRef(request.bucket(), request.prefix()));
        }
    }

    @Override
    public void delete(BlobRef ref) {
        Path path = toPath(provider, ref);
        Lock lock = locks.get(path.toString()).writeLock();
        lock.lock();
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw wrapIOException(e, "delete", ref);
        } finally {
            lock.unlock();
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
                throw new ResourceNotFoundException(provider.name(), "getMetadata", BlobStore.BLOB, ref.getId());
            }
            return new BlobMetadata(ref.bucket(), ref.key(), Files.size(path), Files.probeContentType(path),
                    Files.getLastModifiedTime(path).toMillis(), Map.of());
        } catch (IOException e) {
            throw wrapIOException(e, "getMetadata", ref);
        }
    }

    private BlobMetadata _toMetadata(String bucket, Path bucketPath, Path p) {
        String key = bucketPath.relativize(p).toString();
        try {
            return new BlobMetadata(bucket, key, Files.size(p), Files.probeContentType(p),
                    Files.getLastModifiedTime(p).toMillis(), Map.of());
        } catch (IOException e) {
            throw wrapIOException(e, "list", new BlobRef(bucket, key));
        }
    }

    private void _atomicWrite(Path target, InputStream data) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".tmp-", null);
        try {
            Files.copy(data, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
