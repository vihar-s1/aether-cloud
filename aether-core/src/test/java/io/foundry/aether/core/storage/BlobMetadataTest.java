/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlobMetadataTest {

    @Test
    void recordConstruction() {
        var now = Instant.now();
        var meta = new BlobMetadata("bucket", "key.txt", 1024, "text/plain", now, Map.of("env", "prod"));

        assertThat(meta.bucket()).isEqualTo("bucket");
        assertThat(meta.key()).isEqualTo("key.txt");
        assertThat(meta.sizeBytes()).isEqualTo(1024);
        assertThat(meta.metadata()).containsEntry("env", "prod");
    }

    @Test
    void recordEquality() {
        var now = Instant.now();
        var a = new BlobMetadata("b", "k", 10, "text/plain", now, Map.of());
        var b = new BlobMetadata("b", "k", 10, "text/plain", now, Map.of());
        assertThat(a).isEqualTo(b);
    }
}
