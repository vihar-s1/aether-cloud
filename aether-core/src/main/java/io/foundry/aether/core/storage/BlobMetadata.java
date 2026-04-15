/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import java.time.Instant;
import java.util.Map;

public record BlobMetadata(
        String bucket,
        String key,
        long sizeBytes,
        String contentType,
        Instant lastModified,
        Map<String, String> metadata) {}
