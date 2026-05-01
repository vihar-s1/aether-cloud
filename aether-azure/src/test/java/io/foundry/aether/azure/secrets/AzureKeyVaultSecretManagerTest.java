/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.azure.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.foundry.aether.azure.AzureCloudProvider;
import io.foundry.aether.azure.config.AzureProviderConfig;
import io.foundry.aether.core.exception.ResourceAlreadyExistsException;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AzureKeyVaultSecretManagerTest {

    @Mock
    private SecretClient secretClient;

    private AzureKeyVaultSecretManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        AzureProviderConfig config = AzureProviderConfig.builder().name("test-azure").storageAccount("devstoreaccount1")
                .keyVaultUrl("https://test-vault.vault.azure.net/")
                .enable(io.foundry.aether.core.secrets.SecretManager.class).build();
        AzureCloudProvider provider = new AzureCloudProvider(config);
        manager = new AzureKeyVaultSecretManager(provider, secretClient);
    }

    @Test
    void getSecret_returnsValueAndVersion() {
        SecretProperties props = mock(SecretProperties.class);
        when(props.getVersion()).thenReturn("v1");
        when(props.getCreatedOn()).thenReturn(null);
        KeyVaultSecret secret = mock(KeyVaultSecret.class);
        when(secret.getValue()).thenReturn("my-value");
        when(secret.getProperties()).thenReturn(props);
        when(secretClient.getSecret("my-key")).thenReturn(secret);

        var result = manager.getSecret("my-key");

        assertThat(result.value()).isEqualTo("my-value");
        assertThat(result.secretId()).isEqualTo("my-key");
        assertThat(result.versionId()).isEqualTo("v1");
    }

    @Test
    void getSecret_notFound_throwsResourceNotFoundException() {
        when(secretClient.getSecret("missing")).thenThrow(_notFound("missing"));

        assertThatThrownBy(() -> manager.getSecret("missing"))
                .isInstanceOf(io.foundry.aether.core.exception.ResourceNotFoundException.class);
    }

    @Test
    void createSecret_newKey_setsSecret() {
        when(secretClient.getSecret("new-key")).thenThrow(_notFound("new-key"));

        SecretProperties props = mock(SecretProperties.class);
        when(props.getVersion()).thenReturn("v1");
        when(props.getCreatedOn()).thenReturn(OffsetDateTime.now());
        KeyVaultSecret created = mock(KeyVaultSecret.class);
        when(created.getProperties()).thenReturn(props);
        when(secretClient.setSecret("new-key", "value")).thenReturn(created);

        var meta = manager.createSecret("new-key", "value");

        assertThat(meta.secretId()).isEqualTo("new-key");
        assertThat(meta.versionId()).isEqualTo("v1");
        verify(secretClient).setSecret("new-key", "value");
    }

    @Test
    void createSecret_duplicate_throwsResourceAlreadyExistsException() {
        SecretProperties props = mock(SecretProperties.class);
        when(props.getVersion()).thenReturn("v1");
        KeyVaultSecret existing = mock(KeyVaultSecret.class);
        when(existing.getValue()).thenReturn("v");
        when(existing.getProperties()).thenReturn(props);
        when(secretClient.getSecret("dup-key")).thenReturn(existing);

        assertThatThrownBy(() -> manager.createSecret("dup-key", "value"))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void updateSecret_existing_setsNewValuePreservesCreatedAt() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        SecretProperties existingProps = mock(SecretProperties.class);
        when(existingProps.getVersion()).thenReturn("v1");
        when(existingProps.getCreatedOn()).thenReturn(createdAt);
        KeyVaultSecret existing = mock(KeyVaultSecret.class);
        when(existing.getValue()).thenReturn("old-value");
        when(existing.getProperties()).thenReturn(existingProps);
        when(secretClient.getSecret("my-key")).thenReturn(existing);

        SecretProperties updatedProps = mock(SecretProperties.class);
        when(updatedProps.getVersion()).thenReturn("v2");
        when(updatedProps.getCreatedOn()).thenReturn(createdAt);
        KeyVaultSecret updated = mock(KeyVaultSecret.class);
        when(updated.getProperties()).thenReturn(updatedProps);
        when(secretClient.setSecret("my-key", "new-value")).thenReturn(updated);

        var meta = manager.updateSecret("my-key", "new-value");

        assertThat(meta.versionId()).isEqualTo("v2");
        assertThat(meta.createdAtMs()).isEqualTo(createdAt.toInstant().toEpochMilli());
    }

    @Test
    void updateSecret_notFound_throwsResourceNotFoundException() {
        when(secretClient.getSecret("missing")).thenThrow(_notFound("missing"));

        assertThatThrownBy(() -> manager.updateSecret("missing", "v"))
                .isInstanceOf(io.foundry.aether.core.exception.ResourceNotFoundException.class);
    }

    @Test
    void rotate_returnsNewVersionSameValue() {
        SecretProperties props = mock(SecretProperties.class);
        when(props.getVersion()).thenReturn("v1");
        when(props.getCreatedOn()).thenReturn(OffsetDateTime.now());
        KeyVaultSecret existing = mock(KeyVaultSecret.class);
        when(existing.getValue()).thenReturn("original");
        when(existing.getProperties()).thenReturn(props);
        when(secretClient.getSecret("key")).thenReturn(existing);

        SecretProperties rotatedProps = mock(SecretProperties.class);
        when(rotatedProps.getVersion()).thenReturn("v2");
        KeyVaultSecret rotated = mock(KeyVaultSecret.class);
        when(rotated.getProperties()).thenReturn(rotatedProps);
        when(secretClient.setSecret("key", "original")).thenReturn(rotated);

        var result = manager.rotate("key");

        assertThat(result.value()).isEqualTo("original");
        assertThat(result.versionId()).isEqualTo("v2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSecrets_emptyVault_returnsEmpty() {
        PagedIterable<SecretProperties> paged = mock(PagedIterable.class);
        when(secretClient.listPropertiesOfSecrets()).thenReturn(paged);
        when(paged.iterator()).thenReturn(Collections.<SecretProperties>emptyList().iterator());

        var result = manager.listSecrets(io.foundry.aether.core.ListRequest.first());
        assertThat(result.items()).isEmpty();
    }

    private ResourceNotFoundException _notFound(String name) {
        return new ResourceNotFoundException("Secret not found: " + name, null);
    }
}
