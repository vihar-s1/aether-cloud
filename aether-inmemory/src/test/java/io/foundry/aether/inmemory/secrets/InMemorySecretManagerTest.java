/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.inmemory.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.inmemory.InMemoryCloudProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemorySecretManagerTest {

    private InMemorySecretManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemorySecretManager(new InMemoryCloudProvider());
    }

    @Test
    void putThenGet() {
        manager.putSecret("db-password", "s3cret");

        var secret = manager.getSecret("db-password");
        assertThat(secret.value()).isEqualTo("s3cret");
        assertThat(secret.secretId()).isEqualTo("db-password");
    }

    @Test
    void putSameIdTwice_changesVersion() {
        manager.putSecret("key", "v1");
        var first = manager.getSecret("key");

        manager.putSecret("key", "v2");
        var second = manager.getSecret("key");

        assertThat(second.value()).isEqualTo("v2");
        assertThat(second.versionId()).isNotEqualTo(first.versionId());
    }

    @Test
    void rotate_newVersionSameValue() {
        manager.putSecret("key", "original");
        var before = manager.getSecret("key");

        var rotated = manager.rotate("key");

        assertThat(rotated.value()).isEqualTo("original");
        assertThat(rotated.versionId()).isNotEqualTo(before.versionId());
    }

    @Test
    void deleteThenGet_throws() {
        manager.putSecret("key", "value");
        manager.deleteSecret("key");

        assertThatThrownBy(() -> manager.getSecret("key")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listSecrets() {
        manager.putSecret("a", "1");
        manager.putSecret("b", "2");

        assertThat(manager.listSecrets()).hasSize(2);
    }
}
