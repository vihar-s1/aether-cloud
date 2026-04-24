/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.secrets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.Striped;
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
import io.foundry.aether.nfs.internal.NFSUtils.ResolvedRef;
import io.foundry.aether.nfs.internal.NfsIndexedFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Each secret is a single {@code {base64url(secretId)}.secret} JSON file
 * containing value + metadata, written atomically via temp-file-then-rename —
 * readers always see a complete file. Filenames are Base64-URL-encoded secret
 * IDs (no padding) — collision-free for any secret ID.
 *
 * <p>
 * <b>Intra-JVM safety:</b> {@code Striped<Lock>} (reentrant) serialises
 * concurrent mutations on the same secret; {@code secretsDirLock} serialises
 * index updates for the secrets directory.
 *
 * <p>
 * <b>Cross-JVM safety:</b> {@code createSecret} uses {@code ATOMIC_MOVE}
 * without {@code REPLACE_EXISTING} (POSIX {@code rename(2)}) — fails atomically
 * if the target already exists, no race window. {@code updateSecret},
 * {@code rotate}, and {@code deleteSecret} are last-writer-wins across JVMs;
 * individual file and index operations are each atomic.
 */
@ThreadSafe
public class NFSSecretManager implements SecretManager {

    private static final String SECRETS_DIR = ".aether-nfs/secrets";

    private final NFSCloudProvider provider;
    private final NFSBlobIndex index;
    private final Striped<Lock> fileLocks = Striped.lock(64);
    private final ReentrantReadWriteLock secretsDirLock = new ReentrantReadWriteLock();

    private record SecretFile(@JsonProperty("value") String value, @JsonProperty("metadata") SecretMetadata metadata) {

        @JsonCreator
        SecretFile {
        }
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
        SecretFile entry = _readSecretFile(secretId);
        if (entry == null) {
            throw new ResourceNotFoundException(provider.name(), "getSecret", SECRET, secretId);
        }
        return new SecretValue(secretId, entry.value(), entry.metadata().versionId(), entry.metadata().createdAtMs());
    }

    @Override
    public SecretMetadata createSecret(String secretId, String value) {
        String versionId = String.valueOf(System.nanoTime());
        long createdAt = System.currentTimeMillis();
        SecretMetadata metadata = new SecretMetadata(secretId, secretId, null, versionId, createdAt, 0L);

        Lock fileWriteLock = _fileWriteLock(secretId);
        fileWriteLock.lock();
        try {
            Files.createDirectories(_secretsRoot());
            Path tmp = Files.createTempFile(_secretsRoot(), ".tmp-", null);
            try {
                Files.write(tmp, JsonUtils.toJson(new SecretFile(value, metadata)).getBytes(StandardCharsets.UTF_8));
                Files.move(tmp, _secretPath(secretId), StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                Files.deleteIfExists(tmp);
                throw new InvalidConfigurationException(provider.name(), "createSecret",
                        "Secret already exists: " + secretId);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw NFSUtils.wrapIOException(e, "createSecret", new BlobRef(SECRETS_DIR, secretId));
            }
            secretsDirLock.writeLock().lock();
            try {
                index.addFile(_secretsRoot(), _secretsRoot(), _indexEntry(secretId));
            } finally {
                secretsDirLock.writeLock().unlock();
            }
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "createSecret", new BlobRef(SECRETS_DIR, secretId));
        } finally {
            fileWriteLock.unlock();
        }
        return metadata;
    }

    @Override
    public SecretMetadata updateSecret(String secretId, String value) {
        Lock lock = _fileWriteLock(secretId);
        lock.lock();
        try {
            SecretFile existing = _readSecretFile(secretId);
            if (existing == null) {
                throw new ResourceNotFoundException(provider.name(), "updateSecret", SECRET, secretId);
            }
            String versionId = String.valueOf(System.nanoTime());
            SecretMetadata newMetadata = existing.metadata().updateVersion(versionId);
            // NfsIndexedFile.write() reacquires lock reentrantly
            _resolveIndexedFile(secretId).write(_toInputStream(new SecretFile(value, newMetadata)), "application/json");
            return newMetadata;
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "updateSecret", new BlobRef(SECRETS_DIR, secretId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SecretValue rotate(String secretId) {
        Lock lock = _fileWriteLock(secretId);
        lock.lock();
        try {
            SecretFile existing = _readSecretFile(secretId);
            if (existing == null) {
                throw new ResourceNotFoundException(provider.name(), "rotate", SECRET, secretId);
            }
            String versionId = String.valueOf(System.nanoTime());
            SecretMetadata newMetadata = existing.metadata().updateVersion(versionId);
            // NfsIndexedFile.write() reacquires lock reentrantly
            _resolveIndexedFile(secretId).write(_toInputStream(new SecretFile(existing.value(), newMetadata)),
                    "application/json");
            return new SecretValue(secretId, existing.value(), versionId, existing.metadata().createdAtMs());
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "rotate", new BlobRef(SECRETS_DIR, secretId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteSecret(String secretId) {
        Lock fileWriteLock = _fileWriteLock(secretId);
        fileWriteLock.lock();
        try {
            secretsDirLock.writeLock().lock();
            try {
                try {
                    Files.delete(_secretPath(secretId));
                } catch (NoSuchFileException e) {
                    throw new ResourceNotFoundException(provider.name(), "deleteSecret", SECRET, secretId);
                }
                index.removeFile(_secretsRoot(), _secretsRoot(), _secretFileName(secretId));
            } finally {
                secretsDirLock.writeLock().unlock();
            }
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "deleteSecret", new BlobRef(SECRETS_DIR, secretId));
        } finally {
            fileWriteLock.unlock();
        }
    }

    @Override
    public ListResponse<SecretMetadata> listSecrets(ListRequest<SecretMetadata> request) {
        Path secretsRoot = _secretsRoot();
        if (!Files.exists(secretsRoot)) {
            return ListResponse.empty();
        }
        try {
            List<String> allNames = index.listDirectory(secretsRoot).stream()
                    .filter(e -> !e.isDirectory() && e.name().endsWith(".secret")).map(IndexEntry::name).toList();

            int total = allNames.size();
            int start = _resolveStart(request, total);
            int end = request.limit() != null ? Math.min(start + request.limit(), total) : total;
            boolean hasMore = end < total;

            List<SecretMetadata> items = allNames.subList(start, end).stream().map(name -> {
                try (InputStream stream = Files.newInputStream(secretsRoot.resolve(name))) {
                    return JsonUtils.fromJson(stream, SecretFile.class).metadata();
                } catch (IOException e) {
                    throw NFSUtils.wrapIOException(e, "listSecrets", new BlobRef(SECRETS_DIR, name));
                }
            }).toList();

            return new ListResponse<>(items, null, hasMore);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "listSecrets", new BlobRef(SECRETS_DIR, null));
        }
    }

    private NfsIndexedFile _resolveIndexedFile(String secretId) {
        ResolvedRef resolved = new ResolvedRef(_secretsRoot(), _secretPath(secretId), _secretFileName(secretId));
        return new NfsIndexedFile(resolved, index, _fileWriteLock(secretId), secretsDirLock);
    }

    private Lock _fileWriteLock(String secretId) {
        return fileLocks.get(_secretPath(secretId).toString());
    }

    private SecretFile _readSecretFile(String secretId) {
        Path path = _secretPath(secretId);
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return JsonUtils.fromJson(stream, SecretFile.class);
        } catch (IOException e) {
            throw NFSUtils.wrapIOException(e, "readSecretFile", new BlobRef(SECRETS_DIR, secretId));
        }
    }

    private IndexEntry _indexEntry(String secretId) throws IOException {
        Path path = _secretPath(secretId);
        return IndexEntry.forFile(_secretFileName(secretId), Files.size(path), "application/json",
                Files.getLastModifiedTime(path).toMillis());
    }

    private static InputStream _toInputStream(SecretFile file) {
        return new ByteArrayInputStream(JsonUtils.toJson(file).getBytes(StandardCharsets.UTF_8));
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

    private Path _secretPath(String secretId) {
        return _secretsRoot().resolve(_secretFileName(secretId));
    }

    private static String _secretFileName(String secretId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secretId.getBytes(StandardCharsets.UTF_8))
                + ".secret";
    }
}
