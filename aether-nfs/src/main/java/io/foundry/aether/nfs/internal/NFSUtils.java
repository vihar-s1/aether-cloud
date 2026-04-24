/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.internal;

import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.ExceptionUtils;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NFSUtils {

    private NFSUtils() {
    }

    /**
     * Canonical path resolution for a blob reference. All blob operations use this
     * record instead of computing paths ad-hoc.
     *
     * <p>
     * Given {@code basePath=/data}, {@code bucket=photos},
     * {@code key=2024/jan/img.jpg}:
     * <ul>
     * <li>{@link #bucketRoot()} = {@code /data/photos}</li>
     * <li>{@link #filePath()} = {@code /data/photos/2024/jan/img.jpg}</li>
     * <li>{@link #indexKey()} = {@code "2024/jan/img.jpg"}</li>
     * <li>{@link #dirPath()} = {@code /data/photos/2024/jan}</li>
     * <li>{@link #fileName()} = {@code "img.jpg"}</li>
     * </ul>
     */
    public record ResolvedRef(Path bucketRoot, Path filePath, String indexKey) {

        public Path dirPath() {
            return filePath.getParent();
        }

        public String fileName() {
            return filePath.getFileName().toString();
        }
    }

    /**
     * Resolves a {@link BlobRef} to a {@link ResolvedRef}. Validates the bucket
     * name before resolving.
     */
    public static ResolvedRef resolve(NFSCloudProvider provider, BlobRef ref) {
        validateBucket(ref.bucket());
        Path bucketRoot = Path.of(provider.basePath()).resolve(ref.bucket());
        Path filePath = bucketRoot.resolve(ref.key());
        return new ResolvedRef(bucketRoot, filePath, ref.key());
    }

    /** Returns the bucket root path for the given bucket name. */
    public static Path bucketRoot(NFSCloudProvider provider, String bucket) {
        validateBucket(bucket);
        return Path.of(provider.basePath()).resolve(bucket);
    }

    /**
     * Validates that a bucket name is a flat name without path separators.
     * Consistent with S3, GCS, and Azure where bucket names are flat identifiers.
     *
     * @throws InvalidConfigurationException
     *             if the bucket name is blank or contains path separators
     */
    public static void validateBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new InvalidConfigurationException(NFSCloudProvider.PROVIDER_NAME, "bucket-validation",
                    "Bucket name must not be blank");
        }
        if (bucket.contains("/") || bucket.contains("\\")) {
            throw new InvalidConfigurationException(NFSCloudProvider.PROVIDER_NAME, "bucket-validation",
                    "Bucket name must be a flat name without path separators: " + bucket);
        }
    }

    /**
     * Writes {@code data} to {@code target} atomically via temp-file-then-rename.
     * The parent directory must already exist.
     */
    public static void atomicWrite(Path target, InputStream data) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".tmp-", null);
        try {
            Files.copy(data, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** Writes {@code data} to {@code target} atomically. The parent directory must already exist. */
    public static void atomicWrite(Path target, byte[] data) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".tmp-", null);
        try {
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** Writes {@code content} as UTF-8 to {@code target} atomically. The parent directory must already exist. */
    public static void atomicWrite(Path target, String content) throws IOException {
        atomicWrite(target, content.getBytes(StandardCharsets.UTF_8));
    }

    /** @deprecated Prefer {@link #resolve(NFSCloudProvider, BlobRef)} */
    @Deprecated
    public static Path toPath(NFSCloudProvider provider, BlobRef blobRef) {
        return Path.of(provider.basePath()).resolve(blobRef.bucket()).resolve(blobRef.key());
    }

    public static CloudException wrapIOException(IOException e, String operation, BlobRef ref) {
        return switch (e) {
            case AccessDeniedException ade ->
                new AuthenticationException(NFSCloudProvider.PROVIDER_NAME, operation, BlobStore.BLOB, ade);
            case NoSuchFileException nsfe -> new ResourceNotFoundException(NFSCloudProvider.PROVIDER_NAME, operation,
                    BlobStore.BLOB, ref.getId(), nsfe);
            case FileSystemException fse -> new ProviderUnavailableException(NFSCloudProvider.PROVIDER_NAME, operation,
                    "Filesystem error: " + fse.getMessage(), fse);
            default -> new GenericCloudException(NFSCloudProvider.PROVIDER_NAME, operation, null,
                    "I/O error: " + ExceptionUtils.getRootCause(e), e);
        };
    }
}
