/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

import java.time.Instant;

public record SecretMetadata(
        String secretId, String name, String description, String versionId, Instant createdAt, Instant lastRotatedAt) {}
