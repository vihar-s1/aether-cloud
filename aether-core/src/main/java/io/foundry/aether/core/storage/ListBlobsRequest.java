/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

public record ListBlobsRequest(String bucket, String prefix) {}
