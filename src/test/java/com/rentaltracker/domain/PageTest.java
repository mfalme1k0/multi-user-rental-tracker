package com.rentaltracker.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    @Test
    void acceptsFirstPageWithSmallestPageSize() {
        Page<String> page = new Page<>(List.of("a"), 0, 1, true);

        assertEquals(List.of("a"), page.items());
        assertEquals(0, page.pageNumber());
        assertEquals(1, page.pageSize());
        assertTrue(page.hasNext());
    }

    @Test
    void rejectsNullItems() {
        assertThrows(NullPointerException.class, () -> new Page<String>(null, 0, 1, false));
    }

    @Test
    void rejectsNegativePageNumber() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Page<>(List.of(), -1, 10, false));

        assertEquals("Page number must not be negative", e.getMessage());
    }

    @Test
    void rejectsPageSizeBelowOne() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> new Page<>(List.of(), 0, 0, false));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new Page<>(List.of(), 0, -5, false));

        assertEquals("Page size must be at least 1", zero.getMessage());
        assertEquals("Page size must be at least 1", negative.getMessage());
    }

    @Test
    void copiesItemsSoLaterChangesAndMutationAreImpossible() {
        List<String> source = new ArrayList<>(List.of("a", "b"));
        Page<String> page = new Page<>(source, 2, 5, false);

        source.add("c");

        assertEquals(List.of("a", "b"), page.items());
        assertFalse(page.hasNext());
        assertThrows(UnsupportedOperationException.class, () -> page.items().add("x"));
    }
}
