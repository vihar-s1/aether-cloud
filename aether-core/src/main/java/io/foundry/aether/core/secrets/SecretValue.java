/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.secrets;

public record SecretValue(String secretId, String value, String versionId, long createdAtMs) {}
