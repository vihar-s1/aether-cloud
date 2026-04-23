/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.gcp.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.exception.GenericCloudException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.StringUtils;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import io.foundry.aether.gcp.GcpCloudProvider;
import io.foundry.aether.gcp.internal.GcpUtils;
import java.io.ByteArrayInputStream;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ThreadSafe
public class GcpBlobStore implements BlobStore {

    private final GcpCloudProvider provider;
    private final Storage storage;

    public GcpBlobStore(GcpCloudProvider provider) {
        this.provider = provider;
        this.storage = provider.storageClient();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(request.bucket(), request.key()))
                .setContentType(request.contentType()).build();
        try {
            Blob blob = storage.createFrom(blobInfo, request.data());
            return _toMetadata(request.bucket(), request.key(), blob, request.contentType());
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "upload", BLOB, request.key(), CloudErrorCodes.STORAGE_NOT_FOUND);
        } catch (IOException e) {
            throw new GenericCloudException(GcpCloudProvider.PROVIDER_NAME, "upload", CloudErrorCodes.UNKNOWN,
                    e.getMessage(), e);
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        try {
            Blob blob = storage.get(BlobId.of(ref.bucket(), ref.key()));
            if (blob == null) {
                throw new ResourceNotFoundException(GcpCloudProvider.PROVIDER_NAME, "download", BLOB, ref.getId(), null,
                        CloudErrorCodes.STORAGE_NOT_FOUND);
            }
            BlobMetadata metadata = _toMetadata(ref.bucket(), ref.key(), blob, blob.getContentType());
            return new BlobContent(new ByteArrayInputStream(blob.getContent()), metadata);
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "download", BLOB, ref.getId(), CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public ListResponse<BlobMetadata> list(ListBlobsRequest request) {
        try {
            List<Storage.BlobListOption> opts = new ArrayList<>();
            if (request.prefix() != null && !request.prefix().isEmpty()) {
                opts.add(Storage.BlobListOption.prefix(request.prefix()));
            }
            if (request.cursor() != null && !request.cursor().isEmpty()) {
                opts.add(Storage.BlobListOption.pageToken(request.cursor()));
            }
            if (request.limit() != null) {
                opts.add(Storage.BlobListOption.pageSize(request.limit()));
            }
            var gcpPage = storage.list(request.bucket(), opts.toArray(new Storage.BlobListOption[0]));
            List<BlobMetadata> blobs = new ArrayList<>();
            for (Blob blob : gcpPage.getValues()) {
                blobs.add(new BlobMetadata(request.bucket(), blob.getName(),
                        blob.getSize() != null ? blob.getSize() : 0L, null,
                        blob.getUpdateTimeOffsetDateTime() != null
                                ? blob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()
                                : 0L,
                        Map.of()));
            }
            String nextCursor = gcpPage.getNextPageToken();
            return new ListResponse<>(blobs, nextCursor, !StringUtils.isEmpty(nextCursor));
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "list", BLOB, new BlobRef(request.bucket(), request.prefix()).getId(),
                    CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            storage.delete(BlobId.of(ref.bucket(), ref.key()));
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "delete", BLOB, ref.getId(), CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        try {
            Blob blob = storage.get(BlobId.of(ref.bucket(), ref.key()));
            return blob != null;
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "exists", BLOB, ref.getId(), CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            Blob blob = storage.get(BlobId.of(ref.bucket(), ref.key()));
            if (blob == null) {
                throw new ResourceNotFoundException(GcpCloudProvider.PROVIDER_NAME, "getMetadata", BLOB, ref.getId(),
                        null, CloudErrorCodes.STORAGE_NOT_FOUND);
            }
            return _toMetadata(ref.bucket(), ref.key(), blob, blob.getContentType());
        } catch (StorageException e) {
            throw GcpUtils.wrapGcpException(e, "getMetadata", BLOB, ref.getId(), CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    private BlobMetadata _toMetadata(String bucket, String key, Blob blob, String contentType) {
        long lastModified = blob.getUpdateTimeOffsetDateTime() != null
                ? blob.getUpdateTimeOffsetDateTime().toInstant().toEpochMilli()
                : 0L;
        return new BlobMetadata(bucket, key, blob.getSize() != null ? blob.getSize() : 0L, contentType, lastModified,
                blob.getMetadata());
    }
}
