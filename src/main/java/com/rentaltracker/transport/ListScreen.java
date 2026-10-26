package com.rentaltracker.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;


final class ListScreen {

    static final int PAGE_SIZE = 5;

    @FunctionalInterface
    interface PageSource<T> {
        PageView<T> load(int pageNumber);
    }

    record PageView<T>(List<T> items, boolean hasNext) {
        PageView {
            items = List.copyOf(items);
        }

        static <T> PageView<T> slice(List<T> all, int pageNumber, int pageSize) {
            if (all.isEmpty()) {
                return new PageView<>(List.of(), false);
            }
            int start = pageNumber * pageSize;
            if (start >= all.size()) {
                return new PageView<>(List.of(), false);
            }
            int end = Math.min(start + pageSize, all.size());
            boolean hasNext = end < all.size();
            return new PageView<>(all.subList(start, end), hasNext);
        }
    }

    private final ConsoleIO io;

    ListScreen(ConsoleIO io) {
        this.io = io;
    }

    <T> Optional<T> select(String title, PageSource<T> source, Function<T, String> label) {
        int page = 0;

        while (true) {
            PageView<T> view = source.load(page);
            List<T> items = view.items();

            if (items.isEmpty() && page == 0) {
                return Optional.empty();
            }
            if (items.isEmpty()) {
                page--;  // page > 0 is guaranteed here
                continue;
            }

            io.println();
            io.println("=== " + title + " ===");
            for (int i = 0; i < items.size(); i++) {
                io.println((i + 1) + ") " + label.apply(items.get(i)));
            }

            io.println();
            io.println("B) Back");
            if (page > 0) {
                io.println("P) Previous page");
            }
            if (view.hasNext()) {
                io.println("N) Next page");
            }

            String choice = io.readLine("> ").toUpperCase();

            if (choice.equals("B")) {
                return Optional.empty();
            }
            if (choice.equals("N") && view.hasNext()) {
                page++;
                continue;
            }
            if (choice.equals("P") && page > 0) {
                page--;
                continue;
            }

            Optional<Integer> number = parseNumber(choice);
            if (number.isPresent() && number.get() >= 1 && number.get() <= items.size()) {
                return Optional.of(items.get(number.get() - 1));
            }

            io.println("Invalid option.");
        }
    }

    <T> Optional<T> select(String title, List<T> items, Function<T, String> label) {
        return select(title, page -> PageView.slice(items, page, PAGE_SIZE), label);
    }

    private static Optional<Integer> parseNumber(String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}