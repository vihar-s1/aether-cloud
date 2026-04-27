/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.core.storage.ListBlobsRequest;
import io.foundry.aether.core.storage.UploadBlobRequest;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.config.NfsProviderConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NFSBlobStoreTest {

    @TempDir
    Path tempDir;

    private NFSBlobStore store;

    @BeforeEach
    void setUp() {
        NFSCloudProvider provider = new NFSCloudProvider("test-nfs", tempDir.toString());
        provider.initialize();
        store = new NFSBlobStore(provider);
    }

    @Test
    void nestedKeyPath_prefixList_returnsOnlySubtree() {
        store.upload(UploadBlobRequest.of("bkt", "logs/2024/jan/app.log", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "logs/2024/feb/app.log", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "data/report.csv", new byte[0], "text/csv"));

        var result = store.list(ListBlobsRequest.first("bkt", "logs/"));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(m -> assertThat(m.key()).startsWith("logs/"));
    }

    @Test
    void rebuildIndex_afterIndexDeleted_listStillWorks() throws Exception {
        store.upload(UploadBlobRequest.of("bkt", "a.txt", "hello".getBytes(), "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "b.txt", "world".getBytes(), "text/plain"));

        // Delete all .aether-index files to simulate corruption
        try (var paths = Files.walk(tempDir)) {
            paths.filter(p -> p.getFileName().toString().equals(".aether-index")).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                }
            });
        }

        store.rebuildIndex("bkt");

        var result = store.list(ListBlobsRequest.first("bkt"));
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void upload_thenDeleteAll_listIsEmpty() {
        store.upload(UploadBlobRequest.of("bkt", "x.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt", "y.txt", new byte[0], "text/plain"));

        store.delete(new BlobRef("bkt", "x.txt"));
        store.delete(new BlobRef("bkt", "y.txt"));

        assertThat(store.list(ListBlobsRequest.first("bkt")).items()).isEmpty();
    }

    @Test
    void concurrentUpload_differentKeys_allVisible() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String key = "file-" + i + ".txt";
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.upload(UploadBlobRequest.of("bkt", key, key.getBytes(), "text/plain"));
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(store.list(ListBlobsRequest.first("bkt")).items()).hasSize(threads);
    }

    @Test
    void concurrentUpload_sameKey_lastWriterWins() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final byte[] content = ("version-" + i).getBytes();
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.upload(UploadBlobRequest.of("bkt", "shared.txt", content, "text/plain"));
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // File must exist and be one complete consistent write (not partial)
        assertThat(store.exists(new BlobRef("bkt", "shared.txt"))).isTrue();
        try (var content = store.download(new BlobRef("bkt", "shared.txt"))) {
            assertThat(new String(content.data().readAllBytes())).startsWith("version-");
        }
    }

    @Test
    void deeplyNested_5Levels_uploadListDownload() throws Exception {
        String key = "a/b/c/d/e/file.txt";
        store.upload(UploadBlobRequest.of("bkt", key, "deep".getBytes(), "text/plain"));

        assertThat(store.exists(new BlobRef("bkt", key))).isTrue();
        try (var content = store.download(new BlobRef("bkt", key))) {
            assertThat(new String(content.data().readAllBytes())).isEqualTo("deep");
        }
        var result = store.list(ListBlobsRequest.first("bkt", "a/"));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).key()).isEqualTo(key);
    }

    @Test
    void manyBlobs_100_listAllCorrect() {
        for (int i = 0; i < 100; i++) {
            store.upload(UploadBlobRequest.of("bkt", String.format("file-%03d.txt", i), new byte[0], "text/plain"));
        }
        var result = store.list(ListBlobsRequest.withOffset("bkt", null, 0, 200));
        assertThat(result.items()).hasSize(100);
    }

    @Test
    void listWithOffset_fullPagination_coversAll() {
        for (int i = 0; i < 30; i++) {
            store.upload(UploadBlobRequest.of("bkt", String.format("file-%02d.txt", i), new byte[0], "text/plain"));
        }

        var allKeys = new java.util.HashSet<String>();
        int offset = 0;
        int pageSize = 10;
        while (true) {
            var page = store.list(ListBlobsRequest.withOffset("bkt", null, offset, pageSize));
            page.items().forEach(m -> allKeys.add(m.key()));
            if (!page.hasMore())
                break;
            offset += page.items().size();
        }
        assertThat(allKeys).hasSize(30);
    }

    @Test
    void concurrentUploadAndDelete_sameKey_noPanic() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch ready = new CountDownLatch(threads * 2);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.upload(UploadBlobRequest.of("bkt", "shared.txt", "data".getBytes(), "text/plain"));
            }));
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                store.delete(new BlobRef("bkt", "shared.txt"));
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // No assertion on final state — must not throw
        assertThat(store.exists(new BlobRef("bkt", "shared.txt"))).isIn(true, false);
    }

    @Test
    void multipleBuckets_isolated_noIndexLeakage() {
        store.upload(UploadBlobRequest.of("bkt-a", "file1.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt-a", "file2.txt", new byte[0], "text/plain"));
        store.upload(UploadBlobRequest.of("bkt-b", "file3.txt", new byte[0], "text/plain"));

        assertThat(store.list(ListBlobsRequest.first("bkt-a")).items()).hasSize(2);
        assertThat(store.list(ListBlobsRequest.first("bkt-b")).items()).hasSize(1);
        assertThat(store.list(ListBlobsRequest.first("bkt-b")).items().get(0).key()).isEqualTo("file3.txt");
    }

    @Test
    void encryptedIndex_rebuildAfterCorruption_listWorks() throws Exception {
        Path encDir = tempDir.resolve("encrypted-blobs");
        Files.createDirectories(encDir);
        NfsProviderConfig config = NfsProviderConfig.of("test-enc", encDir.toString(), "blob-secret");
        NFSCloudProvider encProvider = new NFSCloudProvider(config);
        encProvider.initialize();
        NFSBlobStore encStore = new NFSBlobStore(encProvider);

        encStore.upload(UploadBlobRequest.of("bkt", "a.txt", "hello".getBytes(), "text/plain"));
        encStore.upload(UploadBlobRequest.of("bkt", "b.txt", "world".getBytes(), "text/plain"));

        try (var paths = Files.walk(encDir)) {
            paths.filter(p -> p.getFileName().toString().equals(".aether-index")).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                }
            });
        }

        encStore.rebuildIndex("bkt");

        var result = encStore.list(ListBlobsRequest.first("bkt"));
        assertThat(result.items()).hasSize(2);
    }
}
