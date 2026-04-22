/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import static io.foundry.aether.nfs.internal.NFSUtils.toPath;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.JsonUtils;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.internal.NFSUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Not thread-safe: each secret is stored as two separate files (value +
 * metadata). Concurrent reads and writes are not atomic — a reader can observe
 * a new value paired with old metadata or vice versa. The NFS provider is
 * intended for single-threaded development and testing use only.
 */
@NotThreadSafe
public class NFSSecretManager implements SecretManager {

    private static final String SECRETS_BUCKET = ".aether-nfs/secrets";

    private final NFSCloudProvider provider;

    private record Entry(String value, SecretMetadata metadata) {
    }

    public NFSSecretManager(NFSCloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public CloudProvider provider() {
        return provider;
    }

    @Override
    public SecretValue getSecret(String secretId) {
        Entry entry = _readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(provider.name(), "getSecret", SECRET, secretId);
        }
        return new SecretValue(secretId, entry.value(), entry.metadata().versionId(), entry.metadata().createdAtMs());
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        if (_findSecretMetadata(secretId) != null) {
            throw new InvalidConfigurationException(provider.name(), "createSecret",
                    "Secret already exists: " + secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        long createdAt = System.currentTimeMillis();
        SecretMetadata metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, 0L);
        _upsertEntry(secretId, new Entry(value, metadata));
        return metadata;
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        SecretMetadata existing = _findSecretMetadata(secretId);
        if (existing == null) {
            throw new ResourceNotFoundException(provider.name(), "updateSecret", SECRET, secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        SecretMetadata newMetadata = existing.updateVersion(versionId);
        _upsertEntry(secretId, new Entry(value, newMetadata));
        return newMetadata;
    }

    @Override
    public SecretValue rotate(String secretId) {
        Entry entry = _readEntry(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(provider.name(), "rotate", SECRET, secretId);
        }
        String versionId = String.valueOf(System.nanoTime());
        SecretMetadata newMetadata = entry.metadata().updateVersion(versionId);
        _upsertEntry(secretId, new Entry(entry.value(), newMetadata));
        return new SecretValue(secretId, entry.value(), versionId, entry.metadata().createdAtMs());
    }

    @Override
    public void deleteSecret(String secretId) {
        Entry existing = _readEntry(secretId);
        if (existing == null) {
            throw new ResourceNotFoundException(provider.name(), "deleteSecret", SECRET, secretId);
        }
        Path valuePath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        try {
            Files.deleteIfExists(valuePath);
            Files.deleteIfExists(metadataPath);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "deleteSecret", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    @Override
    public List<SecretMetadata> listSecrets() {
        Path secretsBucketPath = Path.of(provider.basePath(), SECRETS_BUCKET);
        if (!Files.exists(secretsBucketPath)) {
            return List.of();
        }
        try {
            return Files.walk(secretsBucketPath, 1).filter(path -> path.getFileName().toString().endsWith(".metadata"))
                    .map(path -> {
                        try (InputStream metadataStream = Files.newInputStream(path)) {
                            return JsonUtils.fromJson(metadataStream, SecretMetadata.class);
                        } catch (IOException e) {
                            throw NFSUtils.wrapIOException(e, "listSecrets",
                                    new BlobRef(SECRETS_BUCKET, path.getFileName().toString()));
                        }
                    }).toList();
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "listSecrets", new BlobRef(SECRETS_BUCKET, null));
        }
    }

    private Entry _readEntry(String secretId) {
        Path valuePath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        if (!Files.exists(valuePath) || !Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream valueStream = Files.newInputStream(valuePath);
                InputStream metadataStream = Files.newInputStream(metadataPath)) {
            String value = new String(valueStream.readAllBytes());
            SecretMetadata metadata = JsonUtils.fromJson(metadataStream, SecretMetadata.class);
            return new Entry(value, metadata);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "readEntry", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    private SecretMetadata _findSecretMetadata(String secretId) {
        Path secretsBucketPath = Path.of(provider.basePath(), SECRETS_BUCKET);
        Path metadataPath = secretsBucketPath.resolve(sanitizeName(secretId) + ".metadata");
        if (!Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream metadataStream = Files.newInputStream(metadataPath)) {
            return JsonUtils.fromJson(metadataStream, SecretMetadata.class);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "findSecretMetadata", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    private void _upsertEntry(String secretId, Entry entry) {
        Path valuePath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(provider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        try {
            Files.createDirectories(valuePath.getParent());
            Files.writeString(valuePath, entry.value());
            Files.writeString(metadataPath, JsonUtils.toJson(entry.metadata()));
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "upsertEntry", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "-");
    }
}
