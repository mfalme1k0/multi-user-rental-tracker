package com.marquee.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Page<T>(int page, int perPage, long total, List<T> items) {

    /**
     * Guards against programmer error only. User-supplied page values are
     * validated in the service layer first, so clients get a 400, not an exception.
     */
    public Page {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (perPage < 1) {
            throw new IllegalArgumentException("perPage must be >= 1");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** SQL OFFSET for this page: {@code LIMIT perPage OFFSET offset()}. */
    public int offset() {
        return (page - 1) * perPage;
    }

    /** Offset for a given page, for use in the repository before a Page exists. */
    public static int offset(int page, int perPage) {
        return (page - 1) * perPage;
    }

    /** Number of pages needed to hold {@code total} items. */
    public long totalPages() {
        return (total + perPage - 1) / perPage;
    }

    /**
     * Builds the JSON-ready body with the list under {@code collectionKey}.
     * LinkedHashMap keeps the field order from the brief.
     */
    public Map<String, Object> toResponse(String collectionKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("per_page", perPage);
        body.put("total", total);
        body.put(collectionKey, items);
        return body;
    }
}