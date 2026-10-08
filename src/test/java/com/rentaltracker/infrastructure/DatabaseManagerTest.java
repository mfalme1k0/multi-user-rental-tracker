package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeCreatesAllTables() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master " +
                             "WHERE type = 'table' " +
                             "AND name IN ('users', 'listed_items', 'rentals') " +
                             "ORDER BY name")) {

            int tableCount = 0;

            while (resultSet.next()) {
                tableCount++;
            }

            assertEquals(3, tableCount);
        }
    }

    @Test
    void rejectsItemWithUnknownOwner() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES
                                (999, 'Bike', 100, 'available')
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("FOREIGN KEY constraint failed")
            );
        }
    }

    @Test
    void rejectsUserWithNullUsername() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO users (username)
                            VALUES (NULL)
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("NOT NULL constraint failed")
            );
        }
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('alice')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO users (username)
                            VALUES ('alice')
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("UNIQUE constraint failed")
            );
        }
    }

    @Test
    void rejectsItemWithNonPositiveCost() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('alice')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES
                                (1, 'Bike', 0, 'available')
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("CHECK constraint failed")
            );
        }
    }

    @Test
    void rejectsItemWithInvalidStatus() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('alice')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES
                                (1, 'Bike', 100, 'broken')
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("CHECK constraint failed")
            );
        }
    }

    @Test
    void rejectsRentalWithInvalidStatus() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('owner')
                    """);

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('renter')
                    """);

            statement.executeUpdate("""
                    INSERT INTO listed_items
                        (owner_id, name, cost_per_day, status)
                    VALUES
                        (1, 'Bike', 100, 'available')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO rentals
                                (item_id, renter_id, start_time, end_time, status)
                            VALUES
                                (
                                    1,
                                    2,
                                    '2026-09-30T10:00:00Z',
                                    '2026-10-01T10:00:00Z',
                                    'overdue'
                                )
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("CHECK constraint failed")
            );
        }
    }

    @Test
    void rejectsRentalWhenEndTimeIsNotAfterStartTime() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('owner')
                    """);

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('renter')
                    """);

            statement.executeUpdate("""
                    INSERT INTO listed_items
                        (owner_id, name, cost_per_day, status)
                    VALUES
                        (1, 'Bike', 100, 'available')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO rentals
                                (item_id, renter_id, start_time, end_time, status)
                            VALUES
                                (
                                    1,
                                    2,
                                    '2026-09-30T10:00:00Z',
                                    '2026-09-30T09:00:00Z',
                                    'active'
                                )
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("CHECK constraint failed")
            );
        }
    }

    @Test
    void rejectsDeletingUserWhoOwnsItem() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO users (username)
                    VALUES ('alice')
                    """);

            statement.executeUpdate("""
                    INSERT INTO listed_items
                        (owner_id, name, cost_per_day, status)
                    VALUES
                        (1, 'Bike', 100, 'available')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            DELETE FROM users
                            WHERE id = 1
                            """)
            );

            assertTrue(
                    exception.getMessage().contains("FOREIGN KEY constraint failed")
            );
        }
    }

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");

        DatabaseManager databaseManager =
                new DatabaseManager(database);

        databaseManager.initialize();

        return databaseManager;
    }

    @Test
    void initializeMigratesLegacyCaseSensitiveUsersTable() throws Exception {
        Path db = tempDir.resolve("legacy.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, "
                    + "created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))");
            s.execute("INSERT INTO users (username) VALUES ('owner'), ('bob')");
        }

        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();

        try (Connection c = manager.getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class,
                    () -> s.execute("INSERT INTO users (username) VALUES ('BOB')"));
            try (ResultSet rs = s.executeQuery("SELECT id FROM users WHERE username = 'bob'")) {
                assertEquals(2, rs.getInt(1));          // ids preserved
            }
        }
        manager.initialize();                            // idempotent: second run is a no-op
    }

    @Test
    void initializeRefusesToMigrateWhenCaseDuplicatesExist() throws Exception {
        Path db = tempDir.resolve("dupes.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, "
                    + "created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))");
            s.execute("INSERT INTO users (username) VALUES ('mfalme'), ('Mfalme')");
        }

        assertThrows(DatabaseConnectionException.class,
                () -> new DatabaseManager(db).initialize());
    }

    @Test
    void triggerRejectsOwnerRentingOwnItem() throws Exception {
        DatabaseManager manager = createDatabase();
        try (Connection c = manager.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (username) VALUES ('owner')");
            s.execute("INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                    + "VALUES (1, 'ladder', '5', 'available')");
            SQLException e = assertThrows(SQLException.class, () -> s.execute(
                    "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                            + "VALUES (1, 1, '2026-10-01', '2026-10-02', 'active')"));
            assertTrue(e.getMessage().contains("owner cannot rent own item"));
        }
    }
}