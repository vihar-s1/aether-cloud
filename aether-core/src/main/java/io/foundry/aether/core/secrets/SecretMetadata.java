/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

public record SecretMetadata(
        String secretId, String name, String description, String versionId, long createdAtMs, long lastRotatedAtMs) {

    public SecretMetadata updateVersion(String newVersionId) {
        return new SecretMetadata(secretId, name, description, newVersionId, createdAtMs, System.currentTimeMillis());
    }
}
