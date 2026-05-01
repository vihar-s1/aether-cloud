/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.internal.AzureUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.internal.StringUtils;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AzureBlobStore implements BlobStore {

    private final AzureCloudProvider provider;
    private final BlobServiceClient blobServiceClient;

    public AzureBlobStore(AzureCloudProvider provider) {
        this.provider = provider;
        this.blobServiceClient = provider.blobServiceClient();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        try {
            BlobContainerClient container = _container(request.bucket());
            container.createIfNotExists();
            BlobClient blob = container.getBlobClient(request.key());
            BlobParallelUploadOptions opts = new BlobParallelUploadOptions(request.data())
                    .setHeaders(new BlobHttpHeaders().setContentType(request.contentType()));
            blob.uploadWithResponse(opts, null, null);
            return new BlobMetadata(request.bucket(), request.key(), request.sizeBytes(), request.contentType(),
                    System.currentTimeMillis(), Map.of());
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "upload", BLOB, request.key());
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        try {
            BlobClient blob = _container(ref.bucket()).getBlobClient(ref.key());
            if (!blob.exists()) {
                throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                        "download", BLOB, ref.getId(), null, CloudErrorCodes.STORAGE_NOT_FOUND);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            blob.downloadStream(out);
            BlobProperties props = blob.getProperties();
            BlobMetadata metadata = new BlobMetadata(ref.bucket(), ref.key(), props.getBlobSize(),
                    props.getContentType(), props.getLastModified().toInstant().toEpochMilli(), Map.of());
            return new BlobContent(new ByteArrayInputStream(out.toByteArray()), metadata);
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "download", BLOB, ref.getId());
        }
    }

    @Override
    public ListResponse<BlobMetadata> list(ListBlobsRequest request) {
        try {
            BlobContainerClient container = _container(request.bucket());
            if (!container.exists()) {
                return ListResponse.empty();
            }
            ListBlobsOptions options = new ListBlobsOptions()
                    .setDetails(new BlobListDetails().setRetrieveMetadata(false));
            if (!StringUtils.isBlank(request.prefix())) {
                options.setPrefix(request.prefix());
            }
            if (request.limit() != null) {
                options.setMaxResultsPerPage(request.limit());
            }

            // Azure SDK uses cursor-based pagination via page tokens; we support offset via
            // skip
            PagedIterable<BlobItem> paged = container.listBlobs(options, null);
            List<BlobMetadata> all = new ArrayList<>();
            paged.forEach(item -> {
                Optional<BlobItemProperties> props = Optional.ofNullable(item.getProperties());
                all.add(new BlobMetadata(request.bucket(), item.getName(),
                        props.map(BlobItemProperties::getContentLength).orElse(0L),
                        props.map(BlobItemProperties::getContentType).orElse(null),
                        props.map(p -> p.getLastModified().toInstant().toEpochMilli()).orElse(0L), Map.of()));
            });

            // Apply offset-based pagination
            int total = all.size();
            int start = request.offset() != null ? Math.max(0, Math.min(request.offset(), total)) : 0;
            int end = request.limit() != null ? Math.min(start + request.limit(), total) : total;
            boolean hasMore = end < total;
            return new ListResponse<>(all.subList(start, end), null, hasMore);
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "list", BLOB, request.bucket());
        }
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            BlobClient blob = _container(ref.bucket()).getBlobClient(ref.key());
            blob.deleteIfExists();
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "delete", BLOB, ref.getId());
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        try {
            return _container(ref.bucket()).getBlobClient(ref.key()).exists();
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "exists", BLOB, ref.getId());
        }
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            BlobClient blob = _container(ref.bucket()).getBlobClient(ref.key());
            if (!blob.exists()) {
                throw new io.foundry.aether.core.exception.ResourceNotFoundException(AzureCloudProvider.PROVIDER_NAME,
                        "getMetadata", BLOB, ref.getId(), null, CloudErrorCodes.STORAGE_NOT_FOUND);
            }
            BlobProperties props = blob.getProperties();
            return new BlobMetadata(ref.bucket(), ref.key(), props.getBlobSize(), props.getContentType(),
                    props.getLastModified().toInstant().toEpochMilli(), Map.of());
        } catch (BlobStorageException e) {
            throw AzureUtils.wrapAzureException(e, "getMetadata", BLOB, ref.getId());
        }
    }

    private BlobContainerClient _container(String bucket) {
        return blobServiceClient.getBlobContainerClient(bucket);
    }
}
