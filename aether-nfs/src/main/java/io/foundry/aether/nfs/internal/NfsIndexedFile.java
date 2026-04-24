/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.internal;

import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.nfs.internal.NFSUtils.ResolvedRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Binds a resolved blob path to its directory's index, ensuring that write and
 * delete operations atomically update both the blob file and the index entry.
 *
 * <p>
 * {@code NfsIndexedFile} is not thread-safe itself — it is obtained fresh from
 * {@code NFSBlobStore._resolve()} on each operation, which provides the
 * appropriate per-file and per-directory locks at creation time.
 *
 * <p>
 * Lock ordering (to prevent deadlock across threads in the same JVM):
 * <ol>
 * <li>File write lock (from the
 * {@link com.google.common.util.concurrent.Striped} pool in
 * {@code NFSBlobStore})</li>
 * <li>Directory write lock (per-directory {@link ReadWriteLock} in
 * {@code NFSBlobStore})</li>
 * <li>{@code FileChannel} lock acquired internally by {@link NFSBlobIndex}</li>
 * </ol>
 */
@NotThreadSafe
public final class NfsIndexedFile {

    private final ResolvedRef resolved;
    private final NFSBlobIndex index;
    private final Lock fileWriteLock;
    private final ReadWriteLock dirLock;

    public NfsIndexedFile(ResolvedRef resolved, NFSBlobIndex index, Lock fileWriteLock, ReadWriteLock dirLock) {
        this.resolved = resolved;
        this.index = index;
        this.fileWriteLock = fileWriteLock;
        this.dirLock = dirLock;
    }

    /**
     * Atomically writes {@code data} to the blob file and registers the file in the
     * directory index. The blob file is written via temp-file-then-rename so
     * concurrent readers always see a complete file.
     *
     * @return metadata for the written blob
     */
    public BlobMetadata write(InputStream data, String contentType) throws IOException {
        fileWriteLock.lock();
        try {
            dirLock.writeLock().lock();
            try {
                Files.createDirectories(resolved.dirPath());
                NFSUtils.atomicWrite(resolved.filePath(), data);
                long size = Files.size(resolved.filePath());
                long lastModified = Files.getLastModifiedTime(resolved.filePath()).toMillis();
                IndexEntry entry = IndexEntry.forFile(resolved.fileName(), size, contentType, lastModified);
                index.addFile(resolved.bucketRoot(), resolved.dirPath(), entry);
                return new BlobMetadata(resolved.bucketRoot().getFileName().toString(), resolved.indexKey(), size,
                        contentType, lastModified, Map.of());
            } finally {
                dirLock.writeLock().unlock();
            }
        } finally {
            fileWriteLock.unlock();
        }
    }

    /**
     * Deletes the blob file and removes its entry from the directory index. If the
     * directory's index becomes empty, the directory entry is pruned from its
     * parent recursively. Deletion of a non-existent file silently succeeds.
     */
    public void delete() throws IOException {
        fileWriteLock.lock();
        try {
            dirLock.writeLock().lock();
            try {
                Files.deleteIfExists(resolved.filePath());
                index.removeFile(resolved.bucketRoot(), resolved.dirPath(), resolved.fileName());
            } finally {
                dirLock.writeLock().unlock();
            }
        } finally {
            fileWriteLock.unlock();
        }
    }

    /** Direct access to the resolved file path for read-only operations. */
    public Path filePath() {
        return resolved.filePath();
    }
}
