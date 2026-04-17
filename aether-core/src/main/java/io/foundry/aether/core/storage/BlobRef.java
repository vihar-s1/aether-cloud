/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import io.foundry.aether.core.internal.StringUtils;

public record BlobRef(String bucket, String key) {

    public String getId() {
        if (StringUtils.isBlank(bucket)) {
            return key;
        }
        return StringUtils.concat(bucket, "/", key);
    }
}
