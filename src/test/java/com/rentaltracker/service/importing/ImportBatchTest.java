package com.rentaltracker.service.importing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportBatchTest {

    @Test
    void defensivelyCopiesAllInputLists() {
        List<CsvUserRow> users = new ArrayList<>();
        List<CsvItemRow> items = new ArrayList<>();
        List<CsvRentalRow> rentals = new ArrayList<>();

        users.add(new CsvUserRow(2, "1", "john", "password", "2025-01-01"));
        items.add(new CsvItemRow(
                2, "10", "1", "Drill", "Power drill",
                "500.00", "AVAILABLE", "2025-01-01"
        ));
        rentals.add(new CsvRentalRow(
                2, "100", "10", "1",
                "2025-01-01T10:00:00",
                "2025-01-02T10:00:00",
                null, "ACTIVE"
        ));

        ImportBatch batch = new ImportBatch(users, items, rentals);

        users.clear();
        items.clear();
        rentals.clear();

        assertAll(
                () -> assertEquals(1, batch.users().size()),
                () -> assertEquals(1, batch.items().size()),
                () -> assertEquals(1, batch.rentals().size())
        );
    }

    @Test
    void returnedListsAreImmutable() {
        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "AVAILABLE", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "100", "10", "1",
                        "2025-01-01T10:00:00",
                        "2025-01-02T10:00:00",
                        null, "ACTIVE"
                ))
        );

        assertAll(
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> batch.users().clear()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> batch.items().clear()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> batch.rentals().clear()
                )
        );
    }

    @Test
    void acceptsEmptyLists() {
        ImportBatch batch = new ImportBatch(
                List.of(),
                List.of(),
                List.of()
        );

        assertAll(
                () -> assertTrue(batch.users().isEmpty()),
                () -> assertTrue(batch.items().isEmpty()),
                () -> assertTrue(batch.rentals().isEmpty())
        );
    }

    @Test
    void rejectsNullLists() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(null, List.of(), List.of())
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(List.of(), null, List.of())
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(List.of(), List.of(), null)
                )
        );
    }

    @Test
    void rejectsNullElements() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(
                                java.util.Arrays.asList((CsvUserRow) null),
                                List.of(),
                                List.of()
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(
                                List.of(),
                                java.util.Arrays.asList((CsvItemRow) null),
                                List.of()
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ImportBatch(
                                List.of(),
                                List.of(),
                                java.util.Arrays.asList((CsvRentalRow) null)
                        )
                )
        );
    }
}