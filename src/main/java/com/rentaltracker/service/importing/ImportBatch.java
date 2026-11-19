package com.rentaltracker.service.importing;

import java.util.List;

//all three dependencies that make up a single import run
public record ImportBatch(
        List<CsvUserRow> users,
        List<CsvItemRow> items,
        List<CsvRentalRow> rentals
) {
    public ImportBatch {
        users = List.copyOf(users);
        items = List.copyOf(items);
        rentals = List.copyOf(rentals);
    }
}