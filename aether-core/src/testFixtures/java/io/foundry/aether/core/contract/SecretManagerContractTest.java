/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.exception.InvalidConfigurationException;
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

    @Test
    void getNonexistent_throws() {
        assertThatThrownBy(() -> manager.getSecret("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createDuplicate_throws() {
        manager.createSecret("key", "v1");
        assertThatThrownBy(() -> manager.createSecret("key", "v2")).isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void updateNonexistent_throws() {
        assertThatThrownBy(() -> manager.updateSecret("missing", "v1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    protected void deleteNonexistent_throws() {
        assertThatThrownBy(() -> manager.deleteSecret("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSecret_metadataHasVersionAndTimestamp() {
        long before = System.currentTimeMillis();
        var meta = manager.createSecret("key", "value");
        assertThat(meta.secretId()).isEqualTo("key");
        assertThat(meta.versionId()).isNotBlank();
        assertThat(meta.createdAtMs()).isGreaterThanOrEqualTo(before);
    }

    @Test
    protected void updateSecret_preservesCreatedAt_bumpsVersion() {
        var created = manager.createSecret("key", "v1");
        var updated = manager.updateSecret("key", "v2");
        assertThat(updated.createdAtMs()).isEqualTo(created.createdAtMs());
        assertThat(updated.versionId()).isNotEqualTo(created.versionId());
        assertThat(manager.getSecret("key").value()).isEqualTo("v2");
    }

    @Test
    void listSecrets_withLimit_returnsPage() {
        manager.putSecret("a", "1");
        manager.putSecret("b", "2");
        manager.putSecret("c", "3");
        var page = manager.listSecrets(ListRequest.withOffset(0, 2));
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void secretId_withSpecialCharacters_createAndGet() {
        manager.createSecret("db/prod/password", "secret");
        var result = manager.getSecret("db/prod/password");
        assertThat(result.value()).isEqualTo("secret");
        assertThat(result.secretId()).isEqualTo("db/prod/password");
    }
}
