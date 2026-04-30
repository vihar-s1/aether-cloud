/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.foundry.aether.core.exception.ResourceAlreadyExistsException;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.config.NfsProviderConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NFSSecretManagerTest {

    @TempDir
    Path tempDir;

    private NFSSecretManager manager;

    @BeforeEach
    void setUp() {
        NFSCloudProvider provider = new NFSCloudProvider("test-nfs", tempDir.toString());
        provider.initialize();
        manager = new NFSSecretManager(provider);
    }

    @Test
    void secretFile_isBase64UrlEncodedFilename() throws IOException {
        manager.createSecret("db/prod/password", "s3cret");

        String expectedFilename = Base64.getUrlEncoder().withoutPadding().encodeToString("db/prod/password".getBytes())
                + ".secret";
        Path secretsDir = tempDir.resolve(".aether-nfs/secrets");
        assertThat(Files.exists(secretsDir.resolve(expectedFilename))).isTrue();
    }

    @Test
    void slashAndHyphenSecretIds_doNotCollide() {
        manager.createSecret("foo/bar", "slash-value");
        manager.createSecret("foo-bar", "hyphen-value");

        assertThat(manager.getSecret("foo/bar").value()).isEqualTo("slash-value");
        assertThat(manager.getSecret("foo-bar").value()).isEqualTo("hyphen-value");
    }

    @Test
    void secretFile_containsValueAndMetadataJson() throws IOException {
        manager.createSecret("my-key", "my-value");

        String filename = Base64.getUrlEncoder().withoutPadding().encodeToString("my-key".getBytes()) + ".secret";
        Path file = tempDir.resolve(".aether-nfs/secrets").resolve(filename);
        String contents = Files.readString(file);

        assertThat(contents).contains("\"value\"");
        assertThat(contents).contains("my-value");
        assertThat(contents).contains("\"metadata\"");
        assertThat(contents).contains("my-key");
    }

    @Test
    void deleteSecret_removedFromIndex_listReturnsEmpty() {
        manager.createSecret("key", "value");
        manager.deleteSecret("key");

        assertThat(manager.listSecrets(io.foundry.aether.core.ListRequest.first()).items()).isEmpty();
    }

    @Test
    void concurrentCreate_sameId_exactlyOneSucceeds() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

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
                try {
                    manager.createSecret("shared-key", "value");
                    succeeded.incrementAndGet();
                } catch (ResourceAlreadyExistsException e) {
                    failed.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(threads - 1);
        assertThat(manager.getSecret("shared-key").value()).isEqualTo("value");
    }

    @Test
    void concurrentUpdate_sameId_lastWriterWins() throws Exception {
        manager.createSecret("key", "initial");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String val = "thread-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                manager.updateSecret("key", val);
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // one of the thread values won — secret must still be readable and consistent
        String finalValue = manager.getSecret("key").value();
        assertThat(finalValue).startsWith("thread-");
    }

    @Test
    void indexEncrypted_listStillWorks() throws IOException {
        Path encDir = tempDir.resolve("encrypted");
        Files.createDirectories(encDir);
        NfsProviderConfig config = NfsProviderConfig.of("test-enc", encDir.toString(), "my-index-secret");
        NFSCloudProvider encProvider = new NFSCloudProvider(config);
        encProvider.initialize();
        NFSSecretManager encManager = new NFSSecretManager(encProvider);

        encManager.createSecret("s1", "val1");
        encManager.createSecret("s2", "val2");

        assertThat(encManager.listSecrets(io.foundry.aether.core.ListRequest.first()).items()).hasSize(2);
        assertThat(encManager.getSecret("s1").value()).isEqualTo("val1");
    }

    @Test
    void rotate_chain_5Times_allVersionsDistinct() {
        var created = manager.createSecret("key", "value");
        var versions = new java.util.HashSet<String>();
        versions.add(created.versionId());

        for (int i = 0; i < 5; i++) {
            versions.add(manager.rotate("key").versionId());
        }

        assertThat(versions).hasSize(6);
        assertThat(manager.getSecret("key").value()).isEqualTo("value");
    }

    @Test
    void createDeleteRecreate_sameId_works() {
        manager.createSecret("key", "first");
        manager.deleteSecret("key");
        manager.createSecret("key", "second");

        var result = manager.getSecret("key");
        assertThat(result.value()).isEqualTo("second");
        assertThat(result.secretId()).isEqualTo("key");
    }

    @Test
    void update_preservesCreatedAt_acrossMultipleUpdates() {
        var created = manager.createSecret("key", "v0");
        long originalCreatedAt = created.createdAtMs();

        for (int i = 1; i <= 5; i++) {
            var updated = manager.updateSecret("key", "v" + i);
            assertThat(updated.createdAtMs()).isEqualTo(originalCreatedAt);
        }
    }

    @Test
    void manySecrets_100_paginationCoversAll() {
        for (int i = 0; i < 100; i++) {
            manager.createSecret(String.format("secret-%03d", i), "value-" + i);
        }

        var allIds = new java.util.HashSet<String>();
        int offset = 0;
        int pageSize = 10;
        while (true) {
            var page = manager.listSecrets(io.foundry.aether.core.ListRequest.withOffset(offset, pageSize));
            page.items().forEach(m -> allIds.add(m.secretId()));
            if (!page.hasMore())
                break;
            offset += page.items().size();
        }
        assertThat(allIds).hasSize(100);
    }

    @Test
    void concurrentMixed_createUpdateDelete_differentIds_noDeadlock() throws Exception {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String id = "concurrent-key-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                manager.createSecret(id, "initial");
                manager.updateSecret(id, "updated");
                manager.deleteSecret(id);
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(manager.listSecrets(io.foundry.aether.core.ListRequest.first()).items()).isEmpty();
    }
}
