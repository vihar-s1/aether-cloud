/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.internal;

import io.foundry.aether.core.exception.AuthenticationException;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.ProviderUnavailableException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.ExceptionUtils;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.nfs.NFSCloudProvider;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class NSFUtils {

    private NSFUtils() {}

    public static Path toPath(NFSCloudProvider provider, BlobRef blobRef) {
        return Path.of(provider.basePath()).resolve(blobRef.bucket()).resolve(blobRef.key());
    }

    public static CloudException wrapIOException(IOException e, String operation, BlobRef ref) {
        return switch (e) {
            case AccessDeniedException ade -> new AuthenticationException(
                    NFSCloudProvider.PROVIDER_NAME, operation, BlobStore.BLOB, ade);
            case NoSuchFileException nsfe -> new ResourceNotFoundException(
                    NFSCloudProvider.PROVIDER_NAME, operation, BlobStore.BLOB, ref.getId(), nsfe);
            case FileSystemException fse -> new ProviderUnavailableException(
                    NFSCloudProvider.PROVIDER_NAME, operation, "Filesystem error: " + fse.getMessage(), fse);
            default -> new GenericCloudException(
                    NFSCloudProvider.PROVIDER_NAME, operation, null, "I/O error: " + ExceptionUtils.getRootCause(e), e);
        };
    }
}
