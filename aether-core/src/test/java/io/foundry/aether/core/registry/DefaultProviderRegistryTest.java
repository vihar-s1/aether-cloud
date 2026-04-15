/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.CloudException;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.storage.BlobStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultProviderRegistryTest {

    private DefaultProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultProviderRegistry();
    }

    private CloudProvider mockProvider(String name) {
        CloudProvider provider = mock(CloudProvider.class);
        when(provider.name()).thenReturn(name);
        return provider;
    }

    @Test
    void registerAndLookup() throws CloudException {
        CloudProvider aws = mockProvider("aws");
        registry.register(aws);

        assertThat(registry.getProvider("aws")).contains(aws);
        assertThat(registry.listProviders()).containsExactly(aws);
    }

    @Test
    void duplicateRegistration_throws() throws CloudException {
        registry.register(mockProvider("aws"));
        assertThatThrownBy(() -> registry.register(mockProvider("aws")))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void unknownProvider_returnsEmpty() {
        assertThat(registry.getProvider("unknown")).isEmpty();
    }

    @Test
    void registerAndGetService() throws CloudException {
        registry.register(mockProvider("aws"));
        BlobStore blobStore = mock(BlobStore.class);

        registry.registerService("aws", BlobStore.class, blobStore);

        assertThat(registry.getService("aws", BlobStore.class)).contains(blobStore);
    }

    @Test
    void registerService_unknownProvider_throws() {
        BlobStore blobStore = mock(BlobStore.class);
        assertThatThrownBy(() -> registry.registerService("unknown", BlobStore.class, blobStore))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("Unknown provider");
    }

    @Test
    void getService_unknownProvider_returnsEmpty() {
        assertThat(registry.getService("unknown", BlobStore.class)).isEmpty();
    }

    @Test
    void concurrentRegistration_doesNotCorruptState() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String name = "provider-" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    registry.register(mockProvider(name));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(failures.get()).isZero();
        assertThat(registry.listProviders()).hasSize(threadCount);
    }
}
