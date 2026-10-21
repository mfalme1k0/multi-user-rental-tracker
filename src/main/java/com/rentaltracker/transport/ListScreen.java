package com.rentaltracker.transport;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;


final class ListScreen {

    static final int PAGE_SIZE = 5;

    private final ConsoleIO io;

    ListScreen(ConsoleIO io) {
        this.io = io;
    }


    <T> Optional<T> select(String title, List<T> items, Function<T, String> label) {
        int page = 0;
        int lastPage = (items.size() - 1) / PAGE_SIZE;

        while (true) {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, items.size());

            io.println();
            io.println("=== " + title + " ===");
            for (int i = start; i < end; i++) {
                io.println((i - start + 1) + ") " + label.apply(items.get(i)));
            }

            io.println();
            io.println("B) Back");
            if (page > 0) {
                io.println("P) Previous page");
            }
            if (page < lastPage) {
                io.println("N) Next page");
            }

            String choice = io.readLine("> ").toUpperCase();

            if (choice.equals("B")) {
                return Optional.empty();
            }
            if (choice.equals("N") && page < lastPage) {
                page++;
                continue;
            }
            if (choice.equals("P") && page > 0) {
                page--;
                continue;
            }

            Optional<Integer> number = parseNumber(choice);
            if (number.isPresent() && number.get() >= 1 && number.get() <= end - start) {
                return Optional.of(items.get(start + number.get() - 1));
            }

            io.println("Invalid option.");
        }
    }

    private static Optional<Integer> parseNumber(String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
