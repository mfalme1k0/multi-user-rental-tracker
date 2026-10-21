package com.rentaltracker.repository;

import java.util.List;
import java.util.Objects;

/** A bounded repository result with enough metadata for caller-side navigation. */
public record Page<T>(List<T> items, int pageNumber, int pageSize, boolean hasNext) {

    public Page {
        Objects.requireNonNull(items, "items");
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }
        items = List.copyOf(items);
    }
}
