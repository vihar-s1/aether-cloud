/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.secrets.SecretManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class SecretManagerContractTest {

    protected abstract SecretManager createSecretManager();

    private SecretManager manager;

    @BeforeEach
    protected void setUp() {
        manager = createSecretManager();
    }

    @Test
    void putThenGet_valueMatches() {
        manager.putSecret("db-pass", "s3cret");
        var secret = manager.getSecret("db-pass");
        assertThat(secret.value()).isEqualTo("s3cret");
        assertThat(secret.secretId()).isEqualTo("db-pass");
    }

    @Test
    void putSameIdNewValue_overwritesWithNewVersion() {
        manager.putSecret("key", "v1");
        var first = manager.getSecret("key");

        manager.putSecret("key", "v2");
        var second = manager.getSecret("key");

        assertThat(second.value()).isEqualTo("v2");
        assertThat(second.versionId()).isNotEqualTo(first.versionId());
    }

    @Test
    void rotate_sameValueNewVersion() {
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
    void rotateNonexistent_throws() {
        assertThatThrownBy(() -> manager.rotate("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listSecrets_returnsAll() {
        manager.putSecret("a", "1");
        manager.putSecret("b", "2");
        assertThat(manager.listSecrets(ListRequest.first()).items()).hasSize(2);
    }

    @Test
    void listEmpty_returnsEmpty() {
        assertThat(manager.listSecrets(ListRequest.first()).items()).isEmpty();
    }
}
