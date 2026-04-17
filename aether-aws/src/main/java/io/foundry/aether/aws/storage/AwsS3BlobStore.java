/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.storage;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.internal.AwsUtils;
import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.internal.StringUtils;
import io.foundry.aether.core.storage.BlobContent;
import io.foundry.aether.core.storage.BlobMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.ListBlobsResponse;
import io.foundry.aether.core.storage.UploadBlobRequest;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class AwsS3BlobStore implements BlobStore {

    private final AwsCloudProvider provider;
    private final S3Client s3Client;

    public AwsS3BlobStore(AwsCloudProvider provider) {
        this.provider = provider;
        var builder = S3Client.builder()
                .region(Region.of(provider.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey(), provider.secretKey())));
        if (!StringUtils.isEmpty(provider.endpoint())) {
            builder.endpointOverride(URI.create(provider.endpoint()));
        }
        builder.forcePathStyle(true);
        this.s3Client = builder.build();
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public BlobMetadata upload(UploadBlobRequest request) {
        var putObjectRequest = PutObjectRequest.builder()
                .bucket(request.bucket())
                .key(request.key())
                .contentType(request.contentType())
                .build();
        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(request.data(), request.sizeBytes()));
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(request.bucket())
                    .key(request.key())
                    .build());
            return new BlobMetadata(
                    request.bucket(),
                    request.key(),
                    response.contentLength(),
                    request.contentType(),
                    response.lastModified().toEpochMilli(),
                    response.metadata());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapS3Exception(e, "upload", BlobStore.BLOB, request.key());
        }
    }

    @Override
    public BlobContent download(BlobRef ref) {
        var getObjectRequest =
                GetObjectRequest.builder().bucket(ref.bucket()).key(ref.key()).build();
        try {
            var data = s3Client.getObject(getObjectRequest);
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(ref.bucket())
                    .key(ref.key())
                    .build());

            var metadata = new BlobMetadata(
                    ref.bucket(),
                    ref.key(),
                    response.contentLength(),
                    response.contentType(),
                    response.lastModified().toEpochMilli(),
                    response.metadata());
            return new BlobContent(data, metadata);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapS3Exception(e, "download", BlobStore.BLOB, ref.getId());
        }
    }

    @Override
    public ListBlobsResponse list(ListBlobsRequest request) {
        var listRequest = ListObjectsV2Request.builder()
                .bucket(request.bucket())
                .prefix(request.prefix())
                .continuationToken(request.cursor())
                .build();
        ListObjectsV2Response response;
        try {
            response = s3Client.listObjectsV2(listRequest);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapS3Exception(
                    e, "list", BlobStore.BLOB, new BlobRef(request.bucket(), request.prefix()).getId());
        }
        var blobs = response.contents().stream()
                .map(s3Object -> new BlobMetadata(
                        request.bucket(),
                        s3Object.key(),
                        s3Object.size(),
                        null,
                        s3Object.lastModified().toEpochMilli(),
                        null))
                .toList();
        return new ListBlobsResponse(blobs, response.nextContinuationToken(), response.isTruncated());
    }

    @Override
    public BlobMetadata delete(BlobRef ref) {
        var deleteRequest = DeleteObjectRequest.builder()
                .bucket(ref.bucket())
                .key(ref.key())
                .build();
        try {
            s3Client.deleteObject(deleteRequest);
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapS3Exception(e, "delete", BlobStore.BLOB, ref.getId());
        }
        return new BlobMetadata(ref.bucket(), ref.key(), 0, null, 0, null);
    }

    @Override
    public boolean exists(BlobRef ref) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(ref.bucket())
                    .key(ref.key())
                    .build());
            return true;
        } catch (NoSuchBucketException | NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public BlobMetadata getMetadata(BlobRef ref) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(ref.bucket())
                    .key(ref.key())
                    .build());
            return new BlobMetadata(
                    ref.bucket(),
                    ref.key(),
                    response.contentLength(),
                    response.contentType(),
                    response.lastModified().toEpochMilli(),
                    response.metadata());
        } catch (AwsServiceException | SdkClientException e) {
            throw AwsUtils.wrapS3Exception(e, "getMetadata", BlobStore.BLOB, ref.getId());
        }
    }
}
