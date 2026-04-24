/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.nfs.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.foundry.aether.core.ListResponse;
import io.foundry.aether.core.internal.StringUtils;
import io.foundry.aether.core.storage.BlobMetadata;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Per-directory index CRUD for NFS blob storage.
 *
 * <p>
 * Each directory on the NFS mount has a {@code .aether-index} file listing its
 * direct children (files and subdirectory names). This allows {@code list()} to
 * read only index files instead of stat-ing every blob.
 *
 * <p>
 * <b>Locking:</b> Every read-modify-write on an index file acquires an
 * exclusive {@link FileLock} via {@code FileChannel.lock()} (blocking) on a
 * companion {@code .aether-index.lock} file. This serialises concurrent writers
 * across JVMs. {@code lockd}/{@code rpcbind} must be running on the NFS mount
 * for cross-host advisory locking to work.
 *
 * <p>
 * <b>Encryption:</b> When an {@code indexSecret} is provided at construction,
 * all index files are written as {@code [12-byte random GCM IV][AES-256-GCM
 * ciphertext]}. The AES key is derived as
 * {@code SHA-256(secret.getBytes(UTF-8))}. Reads and writes are transparently
 * encrypted/decrypted.
 *
 * <p>
 * <b>Consistency:</b> Index files are written atomically via
 * temp-file-then-rename. A reader always observes either the complete old or
 * complete new index, never a partial write.
 */
public final class NFSBlobIndex {

    private static final String INDEX_FILE = ".aether-index";
    private static final String LOCK_FILE = ".aether-index.lock";
    private static final int INDEX_VERSION = 1;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] aesKey;

    public NFSBlobIndex(String indexSecret) {
        this.aesKey = StringUtils.isBlank(indexSecret) ? null : _sha256(indexSecret.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Adds (or replaces) a file entry in {@code dirPath}'s index and ensures all
     * ancestor directories up to {@code bucketRoot} are registered in their
     * respective parent indices.
     */
    public void addFile(Path bucketRoot, Path dirPath, IndexEntry entry) throws IOException {
        _updateIndex(dirPath, entries -> entries.put(entry.name(), entry));
        _ensureParentChain(bucketRoot, dirPath);
    }

    /**
     * Removes a file entry from {@code dirPath}'s index. If the directory's index
     * becomes empty as a result, its entry is pruned from the parent index
     * recursively up to {@code bucketRoot}.
     */
    public void removeFile(Path bucketRoot, Path dirPath, String name) throws IOException {
        boolean[] isEmpty = {false};
        _updateIndex(dirPath, entries -> {
            entries.remove(name);
            isEmpty[0] = entries.isEmpty();
        });
        if (isEmpty[0] && !dirPath.equals(bucketRoot)) {
            _pruneFromParent(bucketRoot, dirPath);
        }
    }

    /**
     * Lists all blobs under {@code bucketRoot} whose key starts with {@code prefix}
     * (or all blobs when {@code prefix} is {@code null}), then applies
     * {@code offset}/{@code limit} for pagination.
     *
     * <p>
     * Results are in sorted key order (DFS traversal over sorted per-directory
     * indices). I/O cost is proportional to the number of directories traversed,
     * not the total number of blobs.
     */
    public ListResponse<BlobMetadata> listRecursive(Path bucketRoot, String prefix, int offset, Integer limit)
            throws IOException {
        List<BlobMetadata> all = new ArrayList<>();
        _collectRecursive(bucketRoot, bucketRoot, prefix, all);
        return _paginate(all, offset, limit);
    }

    /**
     * Returns all direct-child entries in {@code dirPath}'s index in sorted name
     * order. Returns an empty list if no index file exists yet.
     */
    public List<IndexEntry> listDirectory(Path dirPath) throws IOException {
        return new ArrayList<>(_load(dirPath).values());
    }

    /**
     * Rebuilds all {@code .aether-index} files under {@code bucketRoot} by walking
     * the live filesystem. Call this to recover from a missing or corrupt index.
     */
    public void rebuild(Path bucketRoot) throws IOException {
        _rebuildRecursive(bucketRoot);
    }

    // -------------------------------------------------------------------------
    // Index I/O
    // -------------------------------------------------------------------------

    private TreeMap<String, IndexEntry> _load(Path dirPath) throws IOException {
        Path indexPath = dirPath.resolve(INDEX_FILE);
        if (!Files.exists(indexPath) || Files.size(indexPath) == 0) {
            return new TreeMap<>();
        }
        byte[] raw = Files.readAllBytes(indexPath);
        byte[] json = aesKey != null ? _decrypt(raw) : raw;
        IndexData data = MAPPER.readValue(json, IndexData.class);
        return data.entries != null ? data.entries : new TreeMap<>();
    }

    private void _save(Path dirPath, TreeMap<String, IndexEntry> entries) throws IOException {
        Files.createDirectories(dirPath);
        byte[] json = MAPPER.writeValueAsBytes(new IndexData(INDEX_VERSION, entries));
        byte[] toWrite = aesKey != null ? _encrypt(json) : json;
        NFSUtils.atomicWrite(dirPath.resolve(INDEX_FILE), toWrite);
    }

    @FunctionalInterface
    private interface IndexAction {
        void apply(TreeMap<String, IndexEntry> entries) throws IOException;
    }

    private void _updateIndex(Path dirPath, IndexAction action) throws IOException {
        Files.createDirectories(dirPath);
        Path lockPath = dirPath.resolve(LOCK_FILE);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE)) {
            FileLock lock = _acquireLock(channel, lockPath);
            try {
                TreeMap<String, IndexEntry> entries = _load(dirPath);
                action.apply(entries);
                _save(dirPath, entries);
            } finally {
                lock.release();
            }
        }
    }

    private FileLock _acquireLock(FileChannel channel, Path lockPath) throws IOException {
        try {
            return channel.lock();
        } catch (FileLockInterruptionException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for index lock: " + lockPath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Parent chain management
    // -------------------------------------------------------------------------

    private void _ensureParentChain(Path bucketRoot, Path dirPath) throws IOException {
        if (dirPath.equals(bucketRoot)) {
            return;
        }
        Path parentDir = dirPath.getParent();
        String dirName = dirPath.getFileName().toString();
        boolean[] wasNew = {false};
        _updateIndex(parentDir, entries -> {
            if (!entries.containsKey(dirName)) {
                entries.put(dirName, IndexEntry.forDirectory(dirName));
                wasNew[0] = true;
            }
        });
        if (wasNew[0]) {
            _ensureParentChain(bucketRoot, parentDir);
        }
    }

    private void _pruneFromParent(Path bucketRoot, Path dirPath) throws IOException {
        Path parentDir = dirPath.getParent();
        String dirName = dirPath.getFileName().toString();
        boolean[] parentNowEmpty = {false};
        _updateIndex(parentDir, entries -> {
            entries.remove(dirName);
            parentNowEmpty[0] = entries.isEmpty();
        });
        if (parentNowEmpty[0] && !parentDir.equals(bucketRoot)) {
            _pruneFromParent(bucketRoot, parentDir);
        }
    }

    // -------------------------------------------------------------------------
    // List / Collect
    // -------------------------------------------------------------------------

    private void _collectRecursive(Path bucketRoot, Path dirPath, String prefix, List<BlobMetadata> result)
            throws IOException {
        TreeMap<String, IndexEntry> entries = _load(dirPath);
        String bucketName = bucketRoot.getFileName().toString();
        String dirKey = bucketRoot.equals(dirPath) ? "" : bucketRoot.relativize(dirPath).toString().replace('\\', '/');

        for (IndexEntry entry : entries.values()) {
            String entryKey = dirKey.isEmpty() ? entry.name() : dirKey + "/" + entry.name();
            if (entry.isDirectory()) {
                if (_shouldRecurse(entryKey, prefix)) {
                    _collectRecursive(bucketRoot, dirPath.resolve(entry.name()), prefix, result);
                }
            } else {
                if (prefix == null || entryKey.startsWith(prefix)) {
                    result.add(new BlobMetadata(bucketName, entryKey, entry.sizeBytes(), entry.contentType(),
                            entry.lastModifiedMs(), Map.of()));
                }
            }
        }
    }

    /**
     * Returns true if a directory with key {@code dirKey} could contain entries
     * matching {@code prefix}.
     */
    private boolean _shouldRecurse(String dirKey, String prefix) {
        if (prefix == null) {
            return true;
        }
        // prefix is deeper in this subtree: e.g. dirKey="2024", prefix="2024/jan/img"
        if (prefix.startsWith(dirKey + "/")) {
            return true;
        }
        // entire subtree is under the prefix: e.g. dirKey="2024/jan", prefix="2024"
        return dirKey.startsWith(prefix);
    }

    private ListResponse<BlobMetadata> _paginate(List<BlobMetadata> all, int offset, Integer limit) {
        int total = all.size();
        int start = Math.max(0, Math.min(offset, total));
        List<BlobMetadata> slice = all.subList(start, total);
        if (limit != null) {
            boolean hasMore = slice.size() > limit;
            List<BlobMetadata> page = slice.subList(0, Math.min(limit, slice.size()));
            return new ListResponse<>(List.copyOf(page), null, hasMore);
        }
        return new ListResponse<>(List.copyOf(slice), null, false);
    }

    // -------------------------------------------------------------------------
    // Rebuild
    // -------------------------------------------------------------------------

    private void _rebuildRecursive(Path dirPath) throws IOException {
        TreeMap<String, IndexEntry> entries = new TreeMap<>();
        try (var stream = Files.list(dirPath)) {
            List<Path> children = stream.filter(p -> {
                String name = p.getFileName().toString();
                return !name.startsWith(".aether-") && !name.startsWith(".tmp-");
            }).sorted().toList();
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    entries.put(name, IndexEntry.forDirectory(name));
                    _rebuildRecursive(child);
                } else if (Files.isRegularFile(child)) {
                    entries.put(name, IndexEntry.forFile(name, Files.size(child), Files.probeContentType(child),
                            Files.getLastModifiedTime(child).toMillis()));
                }
            }
        }
        _save(dirPath, entries);
    }

    // -------------------------------------------------------------------------
    // Encryption
    // -------------------------------------------------------------------------

    private byte[] _encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Index encryption failed", e);
        }
    }

    private byte[] _decrypt(byte[] data) {
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_LENGTH, data.length);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Index decryption failed", e);
        }
    }

    private static byte[] _sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }

    // -------------------------------------------------------------------------
    // Index file format
    // -------------------------------------------------------------------------

    record IndexData(@JsonProperty("version") int version,
                     @JsonProperty("entries") TreeMap<String, IndexEntry> entries) {

            @JsonCreator
            IndexData(@JsonProperty("version") int version, @JsonProperty("entries") TreeMap<String, IndexEntry> entries) {
                this.version = version;
                this.entries = entries != null ? entries : new TreeMap<>();
            }
        }
}
