/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

import java.util.Objects;

/**
 * Pagination parameters for list operations. Supports two modes:
 * <ul>
 * <li><b>Cursor-based</b> — pass a {@code cursor} value obtained from a prior
 * {@link ListResponse#nextCursor()}. Use {@link #first()} to start from the
 * beginning.</li>
 * <li><b>Offset-based</b> — pass an {@code offset} and {@code limit} for
 * positional pagination.</li>
 * </ul>
 *
 * <p>
 * The type parameter {@code T} matches the item type of the paired
 * {@link ListResponse}, providing compile-time type safety. Subclasses may add
 * service-specific filter fields (e.g., {@code bucket}, {@code prefix}) while
 * inheriting the pagination parameters.
 */
public class ListRequest<T> {

    private final String cursor;
    private final Integer offset;
    private final Integer limit;

    public ListRequest(String cursor, Integer offset, Integer limit) {
        this.cursor = cursor;
        this.offset = offset;
        this.limit = limit;
    }

    public String cursor() {
        return cursor;
    }

    public Integer offset() {
        return offset;
    }

    public Integer limit() {
        return limit;
    }

    public static <T> ListRequest<T> first() {
        return new ListRequest<>(null, null, null);
    }

    public static <T> ListRequest<T> withCursor(String cursor) {
        return new ListRequest<>(cursor, null, null);
    }

    public static <T> ListRequest<T> withCursor(String cursor, int pageSize) {
        return new ListRequest<>(cursor, null, pageSize);
    }

    public static <T> ListRequest<T> withOffset(int offset, int limit) {
        return new ListRequest<>(null, offset, limit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ListRequest<?> that))
            return false;
        return Objects.equals(cursor, that.cursor) && Objects.equals(offset, that.offset)
                && Objects.equals(limit, that.limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cursor, offset, limit);
    }

    @Override
    public String toString() {
        return "ListRequest{cursor=" + cursor + ", offset=" + offset + ", limit=" + limit + "}";
    }
}
