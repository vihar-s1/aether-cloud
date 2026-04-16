/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryBlobStoreTest {

    private InMemoryBlobStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryBlobStore(new InMemoryCloudProvider());
    }

    @Test
    void uploadThenDownload() {
        store.upload(UploadBlobRequest.of("bucket", "file.txt", "hello".getBytes(), "text/plain"));

        try (var content = store.download(new BlobRef("bucket", "file.txt"))) {
            assertThat(content.data()).hasContent("hello");
            assertThat(content.metadata().contentType()).isEqualTo("text/plain");
        }
    }

    @Test
    void uploadThenGetMetadata() {
        store.upload(UploadBlobRequest.of("bucket", "data.bin", new byte[512], "application/octet-stream"));

        var meta = store.getMetadata(new BlobRef("bucket", "data.bin"));
        assertThat(meta.sizeBytes()).isEqualTo(512);
        assertThat(meta.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void listWithPrefixFiltering() {
        store.upload(UploadBlobRequest.of("b", "logs/a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("b", "logs/b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("b", "data/c.txt", new byte[0], "text/plain"));

        var logs = store.list(new ListBlobsRequest("b", "logs/"));
        assertThat(logs).hasSize(2);
    }

    @Test
    void deleteThenExistsReturnsFalse() {
        store.upload(UploadBlobRequest.of("b", "key", new byte[0], "text/plain"));
        assertThat(store.exists(new BlobRef("b", "key"))).isTrue();

        store.delete(new BlobRef("b", "key"));
        assertThat(store.exists(new BlobRef("b", "key"))).isFalse();
    }

    @Test
    void downloadMissingBlob_throws() {
        assertThatThrownBy(() -> store.download(new BlobRef("b", "missing")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
