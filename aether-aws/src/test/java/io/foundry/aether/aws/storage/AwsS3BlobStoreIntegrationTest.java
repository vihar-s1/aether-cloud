/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.aws.storage;

import io.foundry.aether.aws.AwsCloudProvider;
import io.foundry.aether.aws.config.AwsProviderConfig;
import io.foundry.aether.core.contract.BlobStoreContractTest;
import io.foundry.aether.core.storage.BlobStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Tag("integration")
class AwsS3BlobStoreIntegrationTest extends BlobStoreContractTest {

    private static final String[] BUCKETS = {"bucket", "bkt", "empty", "alpha", "beta"};

    private static LocalStackContainer localstack;
    private static S3Client adminClient;

    @BeforeAll
    static void startLocalStack() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0"))
                .withServices("s3");
        localstack.start();
        adminClient = S3Client.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion())).forcePathStyle(true).build();
        for (String bucket : BUCKETS) {
            adminClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    @AfterAll
    static void stopLocalStack() {
        adminClient.close();
        localstack.stop();
    }

    @Override
    protected BlobStore createBlobStore() {
        clearBuckets();
        AwsCloudProvider provider = new AwsCloudProvider(AwsProviderConfig.builder().name("test-aws")
                .accessKey(localstack.getAccessKey()).secretKey(localstack.getSecretKey())
                .endpoint(localstack.getEndpoint().toString()).region(localstack.getRegion())
                .build());
        provider.initialize();
        return new AwsS3BlobStore(provider);
    }

    @Override
    @Test
    @Disabled("S3 uses cursor-based pagination; withOffset second-page navigation is not supported")
    public void listWithOffset_secondPage_correctItems() {
    }

    @Override
    @Test
    @Disabled("S3 LastModified has second-level precision; sub-millisecond >= assertion is unreliable")
    public void metadata_hasLastModifiedTimestamp() {
    }

    private void clearBuckets() {
        for (String bucket : BUCKETS) {
            var objects = adminClient.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).contents();
            if (!objects.isEmpty()) {
                var keys = objects.stream().map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
                adminClient.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(keys).build()).build());
            }
        }
    }
}
