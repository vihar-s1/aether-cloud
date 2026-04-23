/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core.storage;

import io.foundry.aether.core.ListRequest;
import java.util.Objects;

/**
 * Request parameters for {@link BlobStore#list}. Extends {@link ListRequest}
 * with the filter fields specific to blob listing: {@code bucket} and
 * {@code prefix}.
 */
public final class ListBlobsRequest extends ListRequest<BlobMetadata> {

    private final String bucket;
    private final String prefix;

    public ListBlobsRequest(String bucket, String prefix, String cursor, Integer offset, Integer limit) {
        super(cursor, offset, limit);
        this.bucket = bucket;
        this.prefix = prefix;
    }

    public String bucket() {
        return bucket;
    }

    /** {@code null} matches all keys. */
    public String prefix() {
        return prefix;
    }

    public static ListBlobsRequest first(String bucket) {
        return new ListBlobsRequest(bucket, null, null, null, null);
    }

    public static ListBlobsRequest first(String bucket, String prefix) {
        return new ListBlobsRequest(bucket, prefix, null, null, null);
    }

    public static ListBlobsRequest withCursor(String bucket, String prefix, String cursor) {
        return new ListBlobsRequest(bucket, prefix, cursor, null, null);
    }

    public static ListBlobsRequest withCursor(String bucket, String prefix, String cursor, int pageSize) {
        return new ListBlobsRequest(bucket, prefix, cursor, null, pageSize);
    }

    public static ListBlobsRequest withOffset(String bucket, String prefix, int offset, int limit) {
        return new ListBlobsRequest(bucket, prefix, null, offset, limit);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof ListBlobsRequest that))
            return false;
        return Objects.equals(bucket, that.bucket) && Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), bucket, prefix);
    }

    @Override
    public String toString() {
        return "ListBlobsRequest{bucket=" + bucket + ", prefix=" + prefix + ", cursor=" + cursor() + ", offset="
                + offset() + ", limit=" + limit() + "}";
    }
}
