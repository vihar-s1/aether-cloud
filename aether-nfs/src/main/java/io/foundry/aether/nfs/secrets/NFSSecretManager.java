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
    protected void upsertEntry(String secretId, SecretEntry entry) {
        Path valuePath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".value"));
        Path metadataPath = toPath(cloudProvider, new BlobRef(SECRETS_BUCKET, sanitizeName(secretId) + ".metadata"));
        try {
            Files.createDirectories(valuePath.getParent());
            Files.writeString(valuePath, entry.value());
            Files.writeString(metadataPath, JsonUtils.toJson(entry.metadata()));
        } catch (IOException e) {
            throw NSFUtils.wrapIOException(e, "upsertEntry", new BlobRef(SECRETS_BUCKET, secretId));
        }
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

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "-");
    }
}
