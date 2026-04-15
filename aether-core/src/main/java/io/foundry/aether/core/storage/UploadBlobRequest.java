/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import java.io.InputStream;

public record UploadBlobRequest(String bucket, String key, InputStream data, long sizeBytes, String contentType) {

    public static UploadBlobRequest of(String bucket, String key, byte[] data, String contentType) {
        return new UploadBlobRequest(bucket, key, new java.io.ByteArrayInputStream(data), data.length, contentType);
    }
}
