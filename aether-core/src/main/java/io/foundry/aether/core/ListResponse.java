/*
 * Copyright 2026 Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

package io.foundry.aether.core;

import java.util.List;
import java.util.stream.Stream;

/**
 * One page of results from a list operation.
 *
 * @param items
 *            the items on this page
 * @param nextCursor
 *            opaque token to pass to the next {@link ListRequest} to advance to
 *            the following page; {@code null} when there are no more pages
 * @param hasMore
 *            {@code true} if there are more pages after this one
 */
public record ListResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> ListResponse<T> empty() {
        return new ListResponse<>(List.of(), null, false);
    }

    /**
     * Slices a pre-sorted list according to {@code request} and returns the
     * appropriate page. Used by local providers (in-memory, NFS) where all data is
     * available in one shot.
     *
     * <p>
     * Cursor mode: the cursor is an opaque integer string encoding the next start
     * index, produced by a prior call to this method. Offset mode: uses
     * {@link ListRequest#offset()} directly. When both cursor and offset are
     * present, cursor takes precedence.
     */
    public static <T> ListResponse<T> ofPage(List<T> sortedAll, ListRequest<T> request) {
        int total = sortedAll.size();
        int start = _resolveStart(request, total);
        Stream<T> stream = sortedAll.stream().skip(start);
        List<T> page;
        boolean hasMore;
        if (request.limit() != null) {
            page = stream.limit(request.limit()).toList();
            hasMore = start + page.size() < total;
        } else {
            page = stream.toList();
            hasMore = false;
        }
        return new ListResponse<>(page, hasMore ? String.valueOf(start + page.size()) : null, hasMore);
    }

    private static <T> int _resolveStart(ListRequest<T> request, int total) {
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
}
