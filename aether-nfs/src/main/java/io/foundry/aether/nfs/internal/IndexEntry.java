/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single entry in a per-directory {@code .aether-index} file. Represents
 * either a regular file or a subdirectory.
 */
public record IndexEntry(@JsonProperty("name") String name, @JsonProperty("isDirectory") boolean isDirectory,
        @JsonProperty("sizeBytes") long sizeBytes, @JsonProperty("contentType") String contentType,
        @JsonProperty("lastModifiedMs") long lastModifiedMs) {

    @JsonCreator
    public IndexEntry {
    }

    public static IndexEntry forDirectory(String name) {
        return new IndexEntry(name, true, 0L, null, 0L);
    }

    public static IndexEntry forFile(String name, long sizeBytes, String contentType, long lastModifiedMs) {
        return new IndexEntry(name, false, sizeBytes, contentType, lastModifiedMs);
    }
}
