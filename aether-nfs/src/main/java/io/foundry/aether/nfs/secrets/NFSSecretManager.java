/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import static io.foundry.aether.nfs.internal.NSFUtils.toPath;

import io.foundry.aether.core.internal.JsonUtils;
import io.foundry.aether.core.secrets.AbstractSecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.internal.NSFUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class NFSSecretManager extends AbstractSecretManager<NFSCloudProvider> {

    private static final String SECRETS_BUCKET = ".aether-nfs/secrets";

    public NFSSecretManager(NFSCloudProvider provider) {
        super(provider);
    }

    @Override
    protected SecretEntry readEntry(String secretId) {
        Path valuePath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        if (!Files.exists(valuePath) || !Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream valueStream = Files.newInputStream(valuePath);
                InputStream metadataStream = Files.newInputStream(metadataPath)) {
            String value = new String(valueStream.readAllBytes());
            SecretMetadata metadata = JsonUtils.fromJson(metadataStream, SecretMetadata.class);
            return SecretEntry.of(value, metadata);
        } catch (IOException e) {
            throw NSFUtils.wrapIOException(e, "readEntry", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    @Override
    protected Collection<SecretMetadata> listEntries() {
        Path secretsBucketPath = Path.of(cloudProvider.basePath(), SECRETS_BUCKET);

        if (!Files.exists(secretsBucketPath)) {
            return java.util.List.of();
        }

        try {
            return Files.walk(secretsBucketPath, 1)
                    .filter(path -> path.getFileName().toString().endsWith(".metadata"))
                    .map(path -> {
                        try (InputStream metadataStream = Files.newInputStream(path)) {
                            return JsonUtils.fromJson(metadataStream, SecretMetadata.class);
                        } catch (IOException e) {
                            throw NSFUtils.wrapIOException(
                                    e,
                                    "listEntries",
                                    new BlobRef(
                                            SECRETS_BUCKET, path.getFileName().toString()));
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw NSFUtils.wrapIOException(e, "listEntries", new BlobRef(SECRETS_BUCKET, null));
        }
    }

    @Override
    protected SecretMetadata findSecretMetadata(String secretId) {
        Path secretsBucketPath = Path.of(cloudProvider.basePath(), SECRETS_BUCKET);
        Path metadataPath = secretsBucketPath.resolve(sanitizeName(secretId) + ".metadata");
        if (!Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream metadataStream = Files.newInputStream(metadataPath)) {
            return JsonUtils.fromJson(metadataStream, SecretMetadata.class);
        } catch (IOException e) {
            throw NSFUtils.wrapIOException(e, "findSecretMetadata", new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    @Override
    protected VersionInfo createEntry(String secretId, String value) {
        String versionId = String.valueOf(System.nanoTime());
        long createdAt = System.currentTimeMillis();
        SecretMetadata metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, 0L);
        SecretEntry entry = SecretEntry.of(value, metadata);
        _upsertEntry(secretId, entry, "createEntry");
        return new VersionInfo(versionId, createdAt);
    }

    @Override
    protected VersionInfo updateEntry(String secretId, String value) {
        SecretEntry existing = readEntry(secretId);
        String versionId = String.valueOf(System.nanoTime());
        long updatedAt = System.currentTimeMillis();
        SecretMetadata newMetadata = existing.metadata().updateVersion(versionId);
        SecretEntry entry = SecretEntry.of(value, newMetadata);
        _upsertEntry(secretId, entry, "updateEntry");
        return new VersionInfo(versionId, updatedAt);
    }

    @Override
    protected SecretEntry deleteEntry(String secretId) {
        SecretEntry existingEntry = readEntry(secretId);

        if (existingEntry != null) {
            Path valuePath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
            Path metadataPath =
                    toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
            try {
                Files.deleteIfExists(valuePath);
                Files.deleteIfExists(metadataPath);
            } catch (IOException e) {
                throw NSFUtils.wrapIOException(e, "deleteEntry", new BlobRef(SECRETS_BUCKET, secretId));
            }
        }
        return existingEntry;
    }

    private void _upsertEntry(String secretId, SecretEntry entry, String operation) {
        Path valuePath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        try {
            Files.createDirectories(valuePath.getParent());
            Files.writeString(valuePath, entry.value());
            Files.writeString(metadataPath, JsonUtils.toJson(entry.metadata()));
        } catch (IOException e) {
            throw NSFUtils.wrapIOException(e, operation, new BlobRef(SECRETS_BUCKET, secretId));
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "-");
    }
}
