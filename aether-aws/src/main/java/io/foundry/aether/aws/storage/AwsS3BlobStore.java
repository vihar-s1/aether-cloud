/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.storage;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.CloudErrorCodes;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.ListBlobsResponse;
import io.foundry.aether.core.storage.UploadBlobRequest;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class AwsS3BlobStore implements BlobStore {

    private final AwsCloudProvider provider;
    private final S3Client s3Client;

    public AwsS3BlobStore(AwsCloudProvider provider) {
        this.provider = provider;
        this.s3Client = provider.s3Client();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        var putObjectRequest = PutObjectRequest.builder().bucket(request.bucket()).key(request.key())
                .contentType(request.contentType()).build();
        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(request.data(), request.sizeBytes()));
            return new BlobMetadata(request.bucket(), request.key(), request.sizeBytes(), request.contentType(),
                    System.currentTimeMillis(), Map.of());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "upload", BlobStore.BLOB, request.key(),
                    CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        var getObjectRequest = GetObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build();
        try {
            var data = s3Client.getObject(getObjectRequest);
            var response = data.response();
            var metadata = new BlobMetadata(ref.bucket(), ref.key(), response.contentLength(), response.contentType(),
                    response.lastModified().toEpochMilli(), response.metadata());
            return new BlobContent(data, metadata);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "download", BlobStore.BLOB, ref.getId(),
                    CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public ListBlobsResponse list(ListBlobsRequest request) {
        var listRequest = ListObjectsV2Request.builder().bucket(request.bucket()).prefix(request.prefix())
                .continuationToken(request.cursor()).build();
        ListObjectsV2Response response;
        try {
            response = s3Client.listObjectsV2(listRequest);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "list", BlobStore.BLOB,
                    new BlobRef(request.bucket(), request.prefix()).getId(), CloudErrorCodes.STORAGE_NOT_FOUND);
        }
        var blobs = response.contents().stream().map(s3Object -> new BlobMetadata(request.bucket(), s3Object.key(),
                s3Object.size(), null, s3Object.lastModified().toEpochMilli(), null)).toList();
        return new ListBlobsResponse(blobs, response.nextContinuationToken(), response.isTruncated());
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "delete", BlobStore.BLOB, ref.getId(),
                    CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
            return true;
        } catch (NoSuchBucketException | NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build());
            return new BlobMetadata(ref.bucket(), ref.key(), response.contentLength(), response.contentType(),
                    response.lastModified().toEpochMilli(), response.metadata());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapAwsException(e, "getMetadata", BlobStore.BLOB, ref.getId(),
                    CloudErrorCodes.STORAGE_NOT_FOUND);
        }
    }
}
