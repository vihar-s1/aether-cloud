/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.BlobStore;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class BlobStoreContractTest {

    protected abstract BlobStore createBlobStore();

    private BlobStore store;

    @BeforeEach
    void setUp() {
        this.store = createBlobStore();
    }

    @Test
    void uploadThenDownload_contentMatches() {
        store.upload(UploadBlobRequest.of("bucket", "file.txt", "hello".getBytes(), "text/plain"));

        try (var content = store.download(new BlobRef("bucket", "file.txt"))) {
            assertThat(content.data()).hasContent("hello");
            assertThat(content.metadata().contentType()).isEqualTo("text/plain");
        }
    }

    @Test
    void uploadThenGetMetadata_fieldsCorrect() {
        store.upload(UploadBlobRequest.of("bucket", "file.txt", "hello".getBytes(), "text/plain"));

        var metadata = store.getMetadata(new BlobRef("bucket", "file.txt"));
        assertThat(metadata.bucket()).isEqualTo("bucket");
        assertThat(metadata.key()).isEqualTo("file.txt");
        assertThat(metadata.contentType()).isEqualTo("text/plain");
        assertThat(metadata.sizeBytes()).isEqualTo(5);
    }

    @Test
    void uploadThenExists_returnsTrue() {
        store.upload(UploadBlobRequest.of("b", "key", new byte[0], "text/plain"));
        assertThat(store.exists(new BlobRef("b", "key"))).isTrue();
    }

    @Test
    void deleteThenExists_returnsFalse() {
        store.upload(UploadBlobRequest.of("b", "key", new byte[0], "text/plain"));
        store.delete(new BlobRef("b", "key"));
        assertThat(store.exists(new BlobRef("b", "key"))).isFalse();
    }

    @Test
    void downloadNonexistent_throws() {
        assertThatThrownBy(() -> store.download(new BlobRef("b", "missing")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWithPrefix_returnsOnlyMatching() {
        store.upload(UploadBlobRequest.of("b", "logs/a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("b", "logs/b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("b", "data/c.txt", new byte[0], "text/plain"));

        var result = store.list(new ListBlobsRequest("b", "logs/"));
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(m -> assertThat(m.key()).startsWith("logs/"));
    }

    @Test
    void listEmptyBucket_returnsEmpty() {
        assertThat(store.list(new ListBlobsRequest("empty", ""))).isEmpty();
    }

    @Test
    void uploadOverwritesExistingKey() {
        store.upload(UploadBlobRequest.of("b", "key", "v1".getBytes(), "text/plain"));
        store.upload(UploadBlobRequest.of("b", "key", "v2".getBytes(), "text/plain"));

        try (var content = store.download(new BlobRef("b", "key"))) {
            assertThat(content.data()).hasContent("v2");
        }
    }

    @Test
    void getMetadataNonexistent_throws() {
        assertThatThrownBy(() -> store.getMetadata(new BlobRef("b", "missing")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
