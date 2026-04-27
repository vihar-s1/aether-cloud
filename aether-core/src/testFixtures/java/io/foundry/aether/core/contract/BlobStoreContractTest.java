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
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class BlobStoreContractTest {

    protected abstract BlobStore createBlobStore();

    private BlobStore store;

    @BeforeEach
    protected void setUp() {
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
        store.upload(UploadBlobRequest.of("bkt", "key", new byte[0], "text/plain"));
        assertThat(store.exists(new BlobRef("bkt", "key"))).isTrue();
    }

    @Test
    void deleteThenExists_returnsFalse() {
        store.upload(UploadBlobRequest.of("bkt", "key", new byte[0], "text/plain"));
        store.delete(new BlobRef("bkt", "key"));
        assertThat(store.exists(new BlobRef("bkt", "key"))).isFalse();
    }

    @Test
    void downloadNonexistent_throws() {
        assertThatThrownBy(() -> store.download(new BlobRef("bkt", "missing")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWithPrefix_returnsOnlyMatching() {
        store.upload(UploadBlobRequest.of("bkt", "logs/a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "logs/b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "data/c.txt", new byte[0], "text/plain"));

        var result = store.list(ListBlobsRequest.first("bkt", "logs/"));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(m -> assertThat(m.key()).startsWith("logs/"));
    }

    @Test
    void listEmptyBucket_returnsEmpty() {
        var result = store.list(ListBlobsRequest.first("empty"));
        assertThat(result.items()).isEmpty();
    }

    @Test
    void uploadOverwritesExistingKey() {
        store.upload(UploadBlobRequest.of("bkt", "key", "v1".getBytes(), "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "key", "v2".getBytes(), "text/plain"));

        try (var content = store.download(new BlobRef("bkt", "key"))) {
            assertThat(content.data()).hasContent("v2");
        }
    }

    @Test
    void getMetadataNonexistent_throws() {
        assertThatThrownBy(() -> store.getMetadata(new BlobRef("bkt", "missing")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void existsNonexistent_returnsFalse() {
        assertThat(store.exists(new BlobRef("bkt", "missing"))).isFalse();
    }

    @Test
    void deleteNonexistent_isIdempotent() {
        // delete contract is idempotent — must not throw for missing blob
        store.delete(new BlobRef("bkt", "missing"));
    }

    @Test
    void upload_returnsCorrectSizeAndBucket() {
        var meta = store.upload(UploadBlobRequest.of("bkt", "file.txt", "hello".getBytes(), "text/plain"));
        assertThat(meta.bucket()).isEqualTo("bkt");
        assertThat(meta.key()).isEqualTo("file.txt");
        assertThat(meta.sizeBytes()).isEqualTo(5);
    }

    @Test
    void list_noPrefix_returnsAll() {
        store.upload(UploadBlobRequest.of("bkt", "a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "c.txt", new byte[0], "text/plain"));
        assertThat(store.list(ListBlobsRequest.first("bkt")).items()).hasSize(3);
    }

    @Test
    protected void listWithLimit_paginates() {
        store.upload(UploadBlobRequest.of("bkt", "a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "c.txt", new byte[0], "text/plain"));

        var page = store.list(ListBlobsRequest.withOffset("bkt", null, 0, 2));
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void upload_nestedKeyPath_downloadable() {
        store.upload(UploadBlobRequest.of("bkt", "logs/2024/app.log", "data".getBytes(), "text/plain"));

        assertThat(store.exists(new BlobRef("bkt", "logs/2024/app.log"))).isTrue();
        try (var content = store.download(new BlobRef("bkt", "logs/2024/app.log"))) {
            assertThat(content.data()).hasContent("data");
        }
    }

    @Test
    void upload_emptyContent_sizeIsZero() {
        var meta = store.upload(UploadBlobRequest.of("bkt", "empty.bin", new byte[0], "application/octet-stream"));
        assertThat(meta.sizeBytes()).isZero();
    }

    @Test
    void upload_overwrite_sizeUpdated() {
        store.upload(UploadBlobRequest.of("bkt", "file.txt", "short".getBytes(), "text/plain"));
        var meta = store
                .upload(UploadBlobRequest.of("bkt", "file.txt", "much longer content".getBytes(), "text/plain"));
        assertThat(meta.sizeBytes()).isEqualTo("much longer content".length());
        assertThat(store.getMetadata(new BlobRef("bkt", "file.txt")).sizeBytes())
                .isEqualTo("much longer content".length());
    }

    @Test
    void list_sortedByKey() {
        store.upload(UploadBlobRequest.of("bkt", "c.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", new byte[0], "text/plain"));

        var keys = store.list(ListBlobsRequest.first("bkt")).items().stream().map(m -> m.key()).toList();
        assertThat(keys).isSorted();
    }

    @Test
    protected void list_multipleBuckets_isolated() {
        store.upload(UploadBlobRequest.of("alpha", "file1.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("alpha", "file2.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("beta", "file3.txt", new byte[0], "text/plain"));

        assertThat(store.list(ListBlobsRequest.first("alpha")).items()).hasSize(2);
        assertThat(store.list(ListBlobsRequest.first("beta")).items()).hasSize(1);
        assertThat(store.list(ListBlobsRequest.first("beta")).items())
                .allSatisfy(m -> assertThat(m.bucket()).isEqualTo("beta"));
    }

    @Test
    protected void listWithOffset_secondPage_correctItems() {
        store.upload(UploadBlobRequest.of("bkt", "a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "c.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "d.txt", new byte[0], "text/plain"));

        var page1 = store.list(ListBlobsRequest.withOffset("bkt", null, 0, 2));
        var page2 = store.list(ListBlobsRequest.withOffset("bkt", null, 2, 2));

        assertThat(page1.items()).hasSize(2);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page2.items()).hasSize(2);
        assertThat(page2.hasMore()).isFalse();

        var allKeys = new HashSet<String>();
        page1.items().forEach(m -> allKeys.add(m.key()));
        page2.items().forEach(m -> allKeys.add(m.key()));
        assertThat(allKeys).hasSize(4);
    }

    @Test
    void list_afterDelete_countDecreases() {
        store.upload(UploadBlobRequest.of("bkt", "a.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "c.txt", new byte[0], "text/plain"));

        assertThat(store.list(ListBlobsRequest.first("bkt")).items()).hasSize(3);
        store.delete(new BlobRef("bkt", "b.txt"));
        assertThat(store.list(ListBlobsRequest.first("bkt")).items()).hasSize(2);
    }

    @Test
    protected void metadata_hasLastModifiedTimestamp() {
        long before = System.currentTimeMillis();
        store.upload(UploadBlobRequest.of("bkt", "file.txt", "data".getBytes(), "text/plain"));

        var meta = store.getMetadata(new BlobRef("bkt", "file.txt"));
        assertThat(meta.lastModifiedMs()).isGreaterThanOrEqualTo(before);
    }
}
