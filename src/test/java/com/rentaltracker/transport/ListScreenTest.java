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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListScreenTest {

    @Test
    void selectReturnsEmptyWhenFirstPageHasNoItems() {
        ListScreen screen = screenWithInput("B\n");
        Optional<String> selected = screen.select(
                "Empty",
                page -> new ListScreen.PageView<>(List.of(), false),
                s -> s);
        assertTrue(selected.isEmpty());
    }

    @Test
    void selectPicksItemOnCurrentPage() {
        ListScreen screen = screenWithInput("2\n");
        Optional<String> selected = screen.select(
                "Items",
                List.of("a", "b", "c"),
                s -> s);
        assertEquals(Optional.of("b"), selected);
    }

    @Test
    void selectRequestsNextPageFromSource() {
        AtomicInteger lastPage = new AtomicInteger(-1);
        ListScreen screen = screenWithInput("N\n1\n");

        Optional<String> selected = screen.select(
                "Paged",
                pageNumber -> {
                    lastPage.set(pageNumber);
                    if (pageNumber == 0) {
                        return new ListScreen.PageView<>(List.of("p0-a", "p0-b"), true);
                    }
                    return new ListScreen.PageView<>(List.of("p1-a"), false);
                },
                s -> s);

        assertEquals(Optional.of("p1-a"), selected);
        assertEquals(1, lastPage.get());
    }

    @Test
    void selectBackReturnsEmptyWithoutChoosing() {
        ListScreen screen = screenWithInput("B\n");
        Optional<String> selected = screen.select(
                "Items",
                List.of("only"),
                s -> s);
        assertTrue(selected.isEmpty());
    }

    private static ListScreen screenWithInput(String scriptedInput) {
        ByteArrayInputStream in = new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleIO io = new ConsoleIO(in, new PrintStream(out, true, StandardCharsets.UTF_8));
        return new ListScreen(io);
    }
}