package com.rentaltracker.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    // ==================== Schema constraint tests ====================

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
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES (999, 'Bike', 100, 'available')
                            """)
            );
            assertTrue(exception.getMessage().contains("FOREIGN KEY constraint failed"));
        }
    }

    @Test
    void rejectsUserWithNullUsername() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("INSERT INTO users (username) VALUES (NULL)")
            );
            assertTrue(exception.getMessage().contains("NOT NULL constraint failed"));
        }
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'alice\', \'test-hash\')");
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'alice\', \'test-hash\')")
            );
            assertTrue(exception.getMessage().contains("UNIQUE constraint failed"));
        }
    }

    @Test
    void rejectsItemWithNonPositiveCost() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'alice\', \'test-hash\')");
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES (1, 'Bike', 0, 'available')
                            """)
            );
            assertTrue(exception.getMessage().contains("CHECK constraint failed"));
        }
    }

    @Test
    void rejectsItemWithInvalidStatus() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'alice\', \'test-hash\')");
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                            INSERT INTO listed_items
                                (owner_id, name, cost_per_day, status)
                            VALUES (1, 'Bike', 100, 'broken')
                            """)
            );
            assertTrue(exception.getMessage().contains("CHECK constraint failed"));
        }
    }

    @Test
    void rejectsRentalWithInvalidStatus() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'owner\', \'test-hash\')");
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'renter\', \'test-hash\')");
            statement.executeUpdate("""
                    INSERT INTO listed_items (owner_id, name, cost_per_day, status)
                    VALUES (1, 'Bike', 100, 'available')
                    """);
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                            INSERT INTO rentals
                                (item_id, renter_id, start_time, end_time, status)
                            VALUES (1, 2, '2026-09-30T10:00:00Z', '2026-10-01T10:00:00Z', 'overdue')
                            """)
            );
            assertTrue(exception.getMessage().contains("CHECK constraint failed"));
        }
    }

    @Test
    void rejectsRentalWhenEndTimeIsNotAfterStartTime() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'owner\', \'test-hash\')");
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'renter\', \'test-hash\')");
            statement.executeUpdate("""
                    INSERT INTO listed_items (owner_id, name, cost_per_day, status)
                    VALUES (1, 'Bike', 100, 'available')
                    """);
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                            INSERT INTO rentals
                                (item_id, renter_id, start_time, end_time, status)
                            VALUES (1, 2, '2026-09-30T10:00:00Z', '2026-09-30T09:00:00Z', 'active')
                            """)
            );
            assertTrue(exception.getMessage().contains("CHECK constraint failed"));
        }
    }

    @Test
    void rejectsDeletingUserWhoOwnsItem() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'alice\', \'test-hash\')");
            statement.executeUpdate("""
                    INSERT INTO listed_items (owner_id, name, cost_per_day, status)
                    VALUES (1, 'Bike', 100, 'available')
                    """);
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("DELETE FROM users WHERE id = 1")
            );
            assertTrue(exception.getMessage().contains("FOREIGN KEY constraint failed"));
        }
    }

    @Test
    void triggerRejectsOwnerRentingOwnItem() throws Exception {
        DatabaseManager manager = createDatabase();
        try (Connection c = manager.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (username, password) VALUES ('owner', 'test-hash')");
            s.execute("INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                    + "VALUES (1, 'ladder', 5, 'available')");
            SQLException e = assertThrows(SQLException.class, () -> s.execute(
                    "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                            + "VALUES (1, 1, '2026-10-01', '2026-10-02', 'active')"));
            assertTrue(e.getMessage().contains("owner cannot rent own item"));
        }
    }

    // ==================== Migration & version tests ====================







    @Test
    void initializeDoesNotDowngradeNewerSchemaVersion() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 999");
        }
        databaseManager.initialize();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(resultSet.next());
            assertEquals(999, resultSet.getInt(1));
        }
    }

    // ==================== Constructor & Transaction tests ====================

    @Test
    void constructorRejectsNullDatabasePath() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseManager(null)
        );
        assertEquals("Database path must not be null", exception.getMessage());
    }

    @Test
    void constructorRejectsNullConnectionFactory() {
        Path db = tempDir.resolve("null-factory.db");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseManager(db, null)
        );
        assertEquals("Connection factory must not be null", exception.getMessage());
    }

    @Test
    void beginTransactionRejectsWhenAlreadyActive() throws Exception {
        DatabaseManager manager = createDatabase();
        manager.beginTransaction();
        assertTrue(manager.isTransactionActive());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                manager::beginTransaction
        );
        assertEquals("A transaction is already active", exception.getMessage());
        assertTrue(manager.isTransactionActive());
    }

    @Test
    void commitTransactionRejectsWhenNoActiveTransaction() {
        DatabaseManager manager = createDatabase();
        assertFalse(manager.isTransactionActive());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                manager::commitTransaction
        );
        assertEquals("No active database transaction", exception.getMessage());
    }

    @Test
    void rollbackTransactionRejectsWhenNoActiveTransaction() {
        DatabaseManager manager = createDatabase();
        assertFalse(manager.isTransactionActive());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                manager::rollbackTransaction
        );
        assertEquals("No active database transaction", exception.getMessage());
    }

    @Test
    void beginCommitTransactionHappyPath() throws Exception {
        DatabaseManager manager = createDatabase();
        assertFalse(manager.isTransactionActive());

        manager.beginTransaction();
        assertTrue(manager.isTransactionActive());

        Connection connection = manager.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'tx-user\', \'test-hash\')");
        }

        manager.commitTransaction();
        assertFalse(manager.isTransactionActive());

        try (Connection verify = manager.getConnection();
             Statement statement = verify.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM users WHERE username = 'tx-user'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void beginRollbackTransactionHappyPath() throws Exception {
        DatabaseManager manager = createDatabase();
        assertFalse(manager.isTransactionActive());

        manager.beginTransaction();
        assertTrue(manager.isTransactionActive());

        Connection connection = manager.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username, password) VALUES (\'rollback-user\', \'test-hash\')");
        }

        manager.rollbackTransaction();
        assertFalse(manager.isTransactionActive());

        try (Connection verify = manager.getConnection();
             Statement statement = verify.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM users WHERE username = 'rollback-user'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void releaseConnectionDoesNothingWhenNull() {
        DatabaseManager manager = createDatabase();
        assertDoesNotThrow(() -> manager.releaseConnection(null));
    }

    @Test
    void getConnectionReturnsSameConnectionWhileTransactionIsActive() throws Exception {
        DatabaseManager manager = createDatabase();
        manager.beginTransaction();

        Connection first = manager.getConnection();
        Connection second = manager.getConnection();

        assertSame(first, second);
        assertTrue(manager.isTransactionActive());

        manager.rollbackTransaction();
    }

    // ==================== Fault-injection tests ====================
















// ==================== Helpers ====================

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");
        DatabaseManager databaseManager = new DatabaseManager(database);
        databaseManager.initialize();
        return databaseManager;
    }



    private DatabaseManager createDatabaseManagerForTest() {
        return new DatabaseManager(tempDir.resolve("reflection-test.db"));
    }
}