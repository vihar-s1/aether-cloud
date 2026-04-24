/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.storage;

import static io.foundry.aether.nfs.internal.NFSUtils.bucketRoot;
import static io.foundry.aether.nfs.internal.NFSUtils.resolve;
import static io.foundry.aether.nfs.internal.NFSUtils.validateBucket;
import static io.foundry.aether.nfs.internal.NFSUtils.wrapIOException;

import com.google.common.util.concurrent.Striped;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.internal.NFSBlobIndex;
import io.foundry.aether.nfs.internal.NFSUtils.ResolvedRef;
import io.foundry.aether.nfs.internal.NfsIndexedFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.concurrent.ThreadSafe;

/**
 * NFS-backed blob store with a per-directory {@code .aether-index} that makes
 * {@code list()} O(pageSize) I/O instead of O(bucket size).
 *
 * <p>
 * <b>Write path:</b> every upload or delete goes through
 * {@link NfsIndexedFile}, which atomically updates the blob file
 * (temp-file-then-rename) and its index entry in the same critical section.
 * Index updates can never be accidentally omitted by future methods.
 *
 * <p>
 * <b>Read path:</b> {@code download}, {@code exists}, and {@code getMetadata}
 * bypass the index and go directly to the filesystem. Atomic writes guarantee
 * readers always see a complete file.
 *
 * <p>
 * <b>Locking (single-JVM):</b> a {@link Striped} pool of 64
 * {@link ReadWriteLock}s serialises concurrent writes to the same blob file. A
 * {@link ConcurrentHashMap} of per-directory {@link ReentrantReadWriteLock}s
 * serialises index updates within the JVM before the cross-JVM
 * {@code FileChannel} lock is acquired. Lock ordering: file lock → directory
 * lock → FileChannel lock.
 *
 * <p>
 * <b>Pagination:</b> offset-based only. {@link ListResponse#nextCursor()} is
 * always {@code null}. Cursor-based pagination is not supported.
 *
 * <p>
 * <b>Bucket names</b> must be flat identifiers without path separators,
 * matching the constraint enforced by S3, GCS, and Azure.
 */
@ThreadSafe
public class NFSBlobStore implements BlobStore {

    private final NFSCloudProvider provider;
    private final Striped<ReadWriteLock> fileLocks = Striped.readWriteLock(64);
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> dirLocks = new ConcurrentHashMap<>();
    private final NFSBlobIndex index;

    public NFSBlobStore(NFSCloudProvider provider) {
        this.provider = provider;
        this.index = new NFSBlobIndex(provider.indexSecret().orElse(null));
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        try (InputStream data = request.data()) {
            return _resolve(request.bucket(), request.key()).write(data, request.contentType());
        } catch (IOException e) {
            throw wrapIOException(e, "upload", new BlobRef(request.bucket(), request.key()));
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        try {
            Path path = resolve(provider, ref).filePath();
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException(provider.name(), "download", BLOB, ref.getId());
            }
            BlobMetadata metadata = new BlobMetadata(ref.bucket(), ref.key(), Files.size(path),
                    Files.probeContentType(path), Files.getLastModifiedTime(path).toMillis(), Map.of());
            return new BlobContent(Files.newInputStream(path), metadata);
        } catch (IOException e) {
            throw wrapIOException(e, "download", ref);
        }
    }

    /**
     * Lists blobs in a bucket. Offset-based pagination only — cursor is ignored and
     * {@link ListResponse#nextCursor()} is always {@code null}.
     *
     * <p>
     * Results are sourced from per-directory {@code .aether-index} files. If the
     * bucket has pre-existing files that were never uploaded through this store,
     * call {@link #rebuildIndex(String)} first.
     */
    @Override
    public ListResponse<BlobMetadata> list(ListBlobsRequest request) {
        try {
            Path root = bucketRoot(provider, request.bucket());
            if (!Files.exists(root)) {
                return ListResponse.empty();
            }
            int offset = request.offset() != null ? request.offset() : 0;
            return index.listRecursive(root, request.prefix(), offset, request.limit());
        } catch (IOException e) {
            throw wrapIOException(e, "list", new BlobRef(request.bucket(), request.prefix()));
        }
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            _resolve(ref.bucket(), ref.key()).delete();
        } catch (IOException e) {
            throw wrapIOException(e, "delete", ref);
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        return Files.exists(resolve(provider, ref).filePath());
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            Path path = resolve(provider, ref).filePath();
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException(provider.name(), "getMetadata", BLOB, ref.getId());
            }
            return new BlobMetadata(ref.bucket(), ref.key(), Files.size(path), Files.probeContentType(path),
                    Files.getLastModifiedTime(path).toMillis(), Map.of());
        } catch (IOException e) {
            throw wrapIOException(e, "getMetadata", ref);
        }
    }

    /**
     * Rebuilds all {@code .aether-index} files in {@code bucket} from the live
     * filesystem state. Use this to recover from a missing or corrupt index, or to
     * index files that pre-date this store.
     */
    public void rebuildIndex(String bucket) {
        validateBucket(bucket);
        try {
            index.rebuild(bucketRoot(provider, bucket));
        } catch (IOException e) {
            throw wrapIOException(e, "rebuildIndex", new BlobRef(bucket, null));
        }
    }

    private NfsIndexedFile _resolve(String bucket, String key) {
        ResolvedRef resolved = resolve(provider, new BlobRef(bucket, key));
        Lock fileWriteLock = fileLocks.get(resolved.filePath().toString()).writeLock();
        ReadWriteLock dirLock = dirLocks.computeIfAbsent(resolved.dirPath().toString(),
                k -> new ReentrantReadWriteLock());
        return new NfsIndexedFile(resolved, index, fileWriteLock, dirLock);
    }
}
