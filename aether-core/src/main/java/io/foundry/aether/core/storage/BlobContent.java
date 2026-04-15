/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public record BlobContent(InputStream data, BlobMetadata metadata) implements AutoCloseable {

    @Override
    public void close() {
        try {
            data.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
