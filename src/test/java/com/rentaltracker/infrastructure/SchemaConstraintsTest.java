package com.rentaltracker.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


//  The invariants docs/architecture.md section 6 promises the database enforces itself.
//  JaCoCo cannot see schema.sql, so these tests are the only thing that keeps it honest.

class SchemaConstraintsTest {

    @TempDir
    Path tempDir;

    private DatabaseManager manager;

    @BeforeEach
    void setUp() {
        manager = new DatabaseManager(tempDir.resolve("schema.db"));
        manager.initialize();
    }

    private void exec(String... statements) throws SQLException {
        try (Connection c = manager.getConnection(); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    private void assertRejected(String message, String... statements) {
        SQLException e = assertThrows(SQLException.class, () -> exec(statements));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private static String insertItemWithCost(String cost) {
        return "INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                + "VALUES (1, 'x', '" + cost + "', 'available')";
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1.2.3", ".5", "5.", "0", "0.00", "-1", "1e3", " 5"})
    void costPerDayRejectsMalformedOrNonPositiveValues(String cost) throws Exception {
        exec("INSERT INTO users (username) VALUES ('owner')");

        assertRejected("CHECK constraint failed", insertItemWithCost(cost));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0.5", "0.01", "10", "100.50", "007"})
    void costPerDayAcceptsPositiveDecimals(String cost) throws Exception {
        exec("INSERT INTO users (username) VALUES ('owner')");

        assertDoesNotThrow(() -> exec(insertItemWithCost(cost)));
    }

    @Test
    void itemWithRentalsCannotBeDeleted() throws Exception {
        exec("INSERT INTO users (username) VALUES ('owner'), ('renter')",
                insertItemWithCost("5"),
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (1, 2, '2026-10-01T10:00:00Z', '2026-10-02T10:00:00Z', 'closed')");

        assertRejected("FOREIGN KEY constraint failed", "DELETE FROM listed_items WHERE id = 1");
    }

    @Test
    void renterWithRentalsCannotBeDeleted() throws Exception {
        exec("INSERT INTO users (username) VALUES ('owner'), ('renter')",
                insertItemWithCost("5"),
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (1, 2, '2026-10-01T10:00:00Z', '2026-10-02T10:00:00Z', 'closed')");

        assertRejected("FOREIGN KEY constraint failed", "DELETE FROM users WHERE id = 2");
    }

    @Test
    void foreignKeysAreEnforcedOnEveryNewConnection() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertRejected("FOREIGN KEY constraint failed",
                    "INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                            + "VALUES (999, 'x', '1', 'available')");
        }
    }

    @Test
    void secondActiveRentalIsRejectedButClosedHistoryIsNot() throws Exception {
        exec("INSERT INTO users (username) VALUES ('owner'), ('a'), ('b')",
                insertItemWithCost("5"),
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (1, 2, '2026-10-01T10:00:00Z', '2026-10-02T10:00:00Z', 'closed')",
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (1, 3, '2026-10-03T10:00:00Z', '2026-10-04T10:00:00Z', 'active')");

        assertRejected("UNIQUE constraint failed",
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (1, 2, '2026-10-05T10:00:00Z', '2026-10-06T10:00:00Z', 'active')");
    }
}
