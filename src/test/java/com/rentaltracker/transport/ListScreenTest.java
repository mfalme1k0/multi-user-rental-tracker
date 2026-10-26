package com.rentaltracker.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListScreenTest {

    // -------------------------------------------------------------------------
    // Empty pages
    // -------------------------------------------------------------------------

    @Test
    void emptyFirstPageReturnsEmptyWithoutReadingChoice() {
        // items.isEmpty() && page == 0 → return immediately (input unused)
        Optional<String> selected = screen("1\n").select(
                "Empty",
                page -> new ListScreen.PageView<>(List.of(), false),
                s -> s);
        assertTrue(selected.isEmpty());
    }

    @Test
    void emptyNonFirstPageStepsBackThenPick() {
        // N → page 1 empty → items.isEmpty() && page != 0 → page-- → pick on page 0
        AtomicInteger loads = new AtomicInteger();
        Optional<String> selected = screen("N\n1\n").select("Paging", pageNumber -> {
            loads.incrementAndGet();
            if (pageNumber == 0) {
                return new ListScreen.PageView<>(List.of("keep"), true);
            }
            // first time on page 1: empty (triggers step-back)
            if (loads.get() == 2) {
                return new ListScreen.PageView<>(List.of(), false);
            }
            return new ListScreen.PageView<>(List.of("keep"), true);
        }, s -> s);
        assertEquals(Optional.of("keep"), selected);
    }

    // -------------------------------------------------------------------------
    // Navigation: B / N / P
    // -------------------------------------------------------------------------

    @Test
    void backReturnsEmpty() {
        assertTrue(screen("B\n").select("Items", List.of("only"), s -> s).isEmpty());
    }

    @Test
    void nextPageThenPick() {
        AtomicInteger lastPage = new AtomicInteger(-1);
        Optional<String> selected = screen("N\n1\n").select("Paged", pageNumber -> {
            lastPage.set(pageNumber);
            if (pageNumber == 0) {
                return new ListScreen.PageView<>(List.of("p0"), true);
            }
            return new ListScreen.PageView<>(List.of("p1"), false);
        }, s -> s);
        assertEquals(Optional.of("p1"), selected);
        assertEquals(1, lastPage.get());
    }

    @Test
    void nextWhenNoNextIsInvalidThenPick() {
        // choice "N" but hasNext == false → condition false → invalid → then 1
        Optional<String> selected = screen("N\n1\n").select(
                "Items",
                page -> new ListScreen.PageView<>(List.of("only"), false),
                s -> s);
        assertEquals(Optional.of("only"), selected);
    }

    @Test
    void previousPageWhenPageGreaterThanZero() {
        // N → page 1, P → page 0, pick 1  (covers P && page > 0)
        Optional<String> selected = screen("N\nP\n1\n").select("Paged", pageNumber -> {
            if (pageNumber == 0) {
                return new ListScreen.PageView<>(List.of("first"), true);
            }
            return new ListScreen.PageView<>(List.of("second"), false);
        }, s -> s);
        assertEquals(Optional.of("first"), selected);
    }

    @Test
    void previousOnFirstPageIsInvalidThenPick() {
        // "P" with page == 0 → (P && page > 0) is false
        Optional<String> selected = screen("P\n1\n").select(
                "Items",
                List.of("only"),
                s -> s);
        assertEquals(Optional.of("only"), selected);
    }

    // -------------------------------------------------------------------------
    // Number selection branches
    // -------------------------------------------------------------------------

    @Test
    void selectValidNumber() {
        assertEquals(Optional.of("b"),
                screen("2\n").select("Items", List.of("a", "b", "c"), s -> s));
    }

    @Test
    void selectNumberZeroIsInvalidThenPick() {
        // present, but >= 1 is false
        assertEquals(Optional.of("only"),
                screen("0\n1\n").select("Items", List.of("only"), s -> s));
    }

    @Test
    void selectNumberAboveSizeIsInvalidThenPick() {
        // present and >= 1, but <= size is false
        assertEquals(Optional.of("only"),
                screen("9\n1\n").select("Items", List.of("only"), s -> s));
    }

    @Test
    void selectNonNumberIsInvalidThenPick() {
        // number.isPresent() is false
        assertEquals(Optional.of("only"),
                screen("x\n1\n").select("Items", List.of("only"), s -> s));
    }

    // -------------------------------------------------------------------------
    // Menu chrome: show/hide P and N
    // -------------------------------------------------------------------------

    @Test
    void firstPageWithNextShowsNNotP() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ListScreen screen = screenWith("1\n", out);
        screen.select("T", page -> new ListScreen.PageView<>(List.of("a"), true), s -> s);
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("N) Next page"));
        assertFalse(text.contains("P) Previous page"));
    }

    @Test
    void laterPageWithoutNextShowsPNotN() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ListScreen screen = screenWith("N\n1\n", out);
        screen.select("T", pageNumber -> {
            if (pageNumber == 0) {
                return new ListScreen.PageView<>(List.of("a"), true);
            }
            return new ListScreen.PageView<>(List.of("b"), false);
        }, s -> s);
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("P) Previous page"));
        // last page must not offer Next after moving
        assertTrue(text.contains("b"));
    }

    // -------------------------------------------------------------------------
    // PageView.slice
    // -------------------------------------------------------------------------

    @Test
    void sliceEmptyList() {
        ListScreen.PageView<String> view = ListScreen.PageView.slice(List.of(), 0, 5);
        assertTrue(view.items().isEmpty());
        assertFalse(view.hasNext());
    }

    @Test
    void slicePastEnd() {
        ListScreen.PageView<String> view = ListScreen.PageView.slice(List.of("a"), 3, 5);
        assertTrue(view.items().isEmpty());
        assertFalse(view.hasNext());
    }

    @Test
    void sliceFirstPageWithMore() {
        ListScreen.PageView<String> view = ListScreen.PageView.slice(
                List.of("a", "b", "c", "d", "e", "f"), 0, 5);
        assertEquals(List.of("a", "b", "c", "d", "e"), view.items());
        assertTrue(view.hasNext());
    }

    @Test
    void sliceLastPageWithoutMore() {
        ListScreen.PageView<String> view = ListScreen.PageView.slice(
                List.of("a", "b", "c", "d", "e", "f"), 1, 5);
        assertEquals(List.of("f"), view.items());
        assertFalse(view.hasNext());
    }

    @Test
    void listOverloadUsesSlice() {
        // exercises select(title, List, label) → slice
        assertEquals(Optional.of("a"),
                screen("1\n").select("Items", List.of("a", "b"), s -> s));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static ListScreen screen(String scriptedInput) {
        return screenWith(scriptedInput, new ByteArrayOutputStream());
    }

    private static ListScreen screenWith(String scriptedInput, ByteArrayOutputStream out) {
        return new ListScreen(new ConsoleIO(
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8)));
    }
}