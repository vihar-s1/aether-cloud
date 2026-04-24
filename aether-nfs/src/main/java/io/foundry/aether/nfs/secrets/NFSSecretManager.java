/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import io.foundry.aether.core.CloudProvider;
import io.foundry.aether.core.ListRequest;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.exception.InvalidConfigurationException;
import io.foundry.aether.core.exception.ResourceNotFoundException;
import io.foundry.aether.core.internal.JsonUtils;
import io.foundry.aether.core.secrets.SecretManager;
import io.foundry.aether.core.secrets.SecretMetadata;
import io.foundry.aether.core.secrets.SecretValue;
import io.foundry.aether.core.storage.BlobRef;
import io.foundry.aether.nfs.NFSCloudProvider;
import io.foundry.aether.nfs.internal.IndexEntry;
import io.foundry.aether.nfs.internal.NFSBlobIndex;
import io.foundry.aether.nfs.internal.NFSUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;

/** Each secret is two files (value + metadata); reads and writes are non-atomic. Single-threaded use only. */
@NotThreadSafe
public class NFSSecretManager implements SecretManager {

    private static final String SECRETS_DIR = ".aether-nfs/secrets";

    private final NFSCloudProvider provider;
    private final NFSBlobIndex index;

    private record Entry(String value, SecretMetadata metadata) {
    }

    public NFSSecretManager(NFSCloudProvider provider) {
        this.provider = provider;
        this.index = new NFSBlobIndex(provider.indexSecret().orElse(null));
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
        try {
            Files.deleteIfExists(_valuePath(secretId));
            Files.deleteIfExists(_metadataPath(secretId));
            index.removeFile(_secretsRoot(), _secretsRoot(), _metadataFileName(secretId));
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "deleteSecret", new BlobRef(SECRETS_DIR, secretId));
        }
    }

    @Override
    public ListResponse<SecretMetadata> listSecrets(ListRequest<SecretMetadata> request) {
        Path secretsRoot = _secretsRoot();
        if (!Files.exists(secretsRoot)) {
            return ListResponse.empty();
        }
        try {
            // Read sorted entry names from the index — one index file read, no directory walk
            List<String> allNames = index.listDirectory(secretsRoot).stream()
                    .filter(e -> !e.isDirectory() && e.name().endsWith(".metadata"))
                    .map(IndexEntry::name)
                    .toList();

            // Paginate names
            long total = allNames.size();
            long start = request.offset() != null && request.offset() > 0 ? request.offset() : 0;
            long end = request.limit() != null && request.limit() > 0 ? request.limit() : total;
            boolean hasMore = end < total;

            // Read only the page's metadata files
            List<SecretMetadata> items = allNames.stream().skip(start).limit(end).map(name -> {
                try (InputStream stream = Files.newInputStream(secretsRoot.resolve(name))) {
                    return JsonUtils.fromJson(stream, SecretMetadata.class);
                } catch (IOException e) {
                    throw NFSUtils.wrapIOException(e, "listSecrets", new BlobRef(SECRETS_DIR, name));
                }
            }).toList();

            return new ListResponse<>(items, null, hasMore);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "listSecrets", new BlobRef(SECRETS_DIR, null));
        }
    }

    private Entry _readEntry(String secretId) {
        Path valuePath = _valuePath(secretId);
        Path metadataPath = _metadataPath(secretId);
        if (!Files.exists(valuePath) || !Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream valueStream = Files.newInputStream(valuePath);
                InputStream metadataStream = Files.newInputStream(metadataPath)) {
            String value = new String(valueStream.readAllBytes(), StandardCharsets.UTF_8);
            SecretMetadata metadata = JsonUtils.fromJson(metadataStream, SecretMetadata.class);
            return new Entry(value, metadata);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "readEntry", new BlobRef(SECRETS_DIR, secretId));
        }
    }

    private SecretMetadata _findSecretMetadata(String secretId) {
        Path metadataPath = _metadataPath(secretId);
        if (!Files.exists(metadataPath)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(metadataPath)) {
            return JsonUtils.fromJson(stream, SecretMetadata.class);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "findSecretMetadata", new BlobRef(SECRETS_DIR, secretId));
        }
    }

    private void _upsertEntry(String secretId, Entry entry) {
        Path valuePath = _valuePath(secretId);
        Path metadataPath = _metadataPath(secretId);
        try {
            Files.createDirectories(valuePath.getParent());
            NFSUtils.atomicWrite(valuePath, entry.value());
            NFSUtils.atomicWrite(metadataPath, JsonUtils.toJson(entry.metadata()));
            long size = Files.size(metadataPath);
            long lastModified = Files.getLastModifiedTime(metadataPath).toMillis();
            index.addFile(_secretsRoot(), _secretsRoot(),
                    IndexEntry.forFile(_metadataFileName(secretId), size, "application/json", lastModified));
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "upsertEntry", new BlobRef(SECRETS_DIR, secretId));
        }
    }

    private static int _resolveStart(ListRequest<SecretMetadata> request, int total) {
        int start = 0;
        if (request.cursor() != null) {
            try {
                start = Integer.parseInt(request.cursor());
            } catch (NumberFormatException ignored) {
            }
        } else if (request.offset() != null) {
            start = request.offset();
        }
        return Math.max(0, Math.min(start, total));
    }

    private Path _secretsRoot() {
        return Path.of(provider.basePath()).resolve(SECRETS_DIR);
    }

    private Path _valuePath(String secretId) {
        return _secretsRoot().resolve(sanitizeName(secretId) + ".value");
    }

    private Path _metadataPath(String secretId) {
        return _secretsRoot().resolve(_metadataFileName(secretId));
    }

    private String _metadataFileName(String secretId) {
        return sanitizeName(secretId) + ".metadata";
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "-");
    }
}
