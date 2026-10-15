package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('alice')");
            SQLException exception = assertThrows(SQLException.class, () ->
                    statement.executeUpdate("INSERT INTO users (username) VALUES ('alice')")
            );
            assertTrue(exception.getMessage().contains("UNIQUE constraint failed"));
        }
    }

    @Test
    void rejectsItemWithNonPositiveCost() throws Exception {
        DatabaseManager databaseManager = createDatabase();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (username) VALUES ('alice')");
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('alice')");
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('owner')");
            statement.executeUpdate("INSERT INTO users (username) VALUES ('renter')");
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('owner')");
            statement.executeUpdate("INSERT INTO users (username) VALUES ('renter')");
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('alice')");
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
            s.execute("INSERT INTO users (username) VALUES ('owner')");
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
                assertEquals(2, rs.getInt(1));
            }
        }
        manager.initialize(); // idempotent
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
    void initializeUpdatesVersionWhenUsersTableAlreadyUsesNoCase() throws Exception {
        Path db = tempDir.resolve("already-no-case.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
                )
                """);
            statement.execute("INSERT INTO users (username) VALUES ('alice')");
            statement.execute("PRAGMA user_version = 0");
        }
        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();
        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(resultSet.next());
            assertEquals(DatabaseManager.SCHEMA_VERSION, resultSet.getInt(1));
        }
        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () ->
                    statement.executeUpdate("INSERT INTO users (username) VALUES ('ALICE')"));
        }
    }

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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('tx-user')");
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
            statement.executeUpdate("INSERT INTO users (username) VALUES ('rollback-user')");
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

    @Test
    void migrationFailsWhenCaseOnlyDuplicatesExist() throws Exception {
        Path db = tempDir.resolve("case-duplicates.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL UNIQUE,
                created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
            )
            """);
            s.execute("INSERT INTO users (username) VALUES ('Alice')");
            s.execute("INSERT INTO users (username) VALUES ('alice')");
        }

        DatabaseManager manager = new DatabaseManager(db);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertTrue(
                exception.getCause().getMessage().contains("differing only by case")
                        || exception.getMessage().contains("differing only by case")
        );
    }

    @Test
    void migrationFailurePreservesOriginalExceptionWhenRollbackSucceeds() throws Exception {
        Path db = createLegacyDatabase("migration-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failMigrationInsert = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals("Forced migration failure", exception.getCause().getMessage());
        assertEquals(0, exception.getCause().getSuppressed().length);
        assertTrue(plan.rollbackAttempted);
    }

    @Test
    void migrationFailureSuppressesRollbackFailure() throws Exception {
        Path db = createLegacyDatabase("rollback-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failMigrationInsert = true;
        plan.failRollback = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        Throwable original = exception.getCause();
        assertEquals("Forced migration failure", original.getMessage());
        assertEquals(1, original.getSuppressed().length);
        assertEquals("Forced rollback failure", original.getSuppressed()[0].getMessage());
        assertTrue(plan.rollbackAttempted);
    }

    @Test
    void cleanupFailureIsRecordedWhenAutoCommitRestoreFails() throws Exception {
        Path db = createLegacyDatabase("autocommit-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failGetAutoCommit = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        // The forced getAutoCommit failure should surface
        assertTrue(
                exception.getCause().getMessage().contains("Forced getAutoCommit failure")
                        || exception.getMessage().contains("Forced getAutoCommit failure")
        );
    }

    @Test
    void foreignKeyReenableFailureIsRecorded() throws Exception {
        Path db = createLegacyDatabase("fk-reenable-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failForeignKeyReenable = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertTrue(
                exception.getCause().getMessage().contains("Forced foreign-key reenable failure")
                        || exception.getMessage().contains("Forced foreign-key reenable failure")
        );
    }

    @Test
    void migrationFailsWhenUsersTableCannotBeInspected() throws Exception {
        Path db = createLegacyDatabase("missing-table.db");
        FaultPlan plan = new FaultPlan();
        plan.usersTableQueryReturnsNoRows = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertTrue(
                exception.getCause().getMessage().contains("Cannot inspect users table")
                        || exception.getMessage().contains("Cannot inspect users table")
        );
    }

    @Test
    void migrationRollsBackWhenForeignKeyCheckFindsViolations() throws Exception {
        Path db = createLegacyDatabase("fk-check-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failForeignKeyCheck = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertTrue(
                exception.getCause().getMessage().contains("dangling foreign keys")
                        || exception.getMessage().contains("dangling foreign keys")
        );
        assertTrue(plan.rollbackAttempted);
    }


// ==================== Fault injection helpers ====================

    private static final class FaultPlan {
        boolean failMigrationInsert;
        boolean failRollback;
        boolean failGetAutoCommit;
        boolean failForeignKeyReenable;
        boolean failForeignKeyCheck;
        boolean usersTableQueryReturnsNoRows;
        boolean rollbackAttempted;
        int foreignKeyEnableAttempts;
    }

    private DatabaseManager managerWithFaults(Path db, FaultPlan plan) {
        return new DatabaseManager(db, () -> connectionWithFaults(
                DriverManager.getConnection("jdbc:sqlite:" + db), plan));
    }

    private static Connection connectionWithFaults(Connection delegate, FaultPlan plan) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if (name.equals("rollback") && method.getParameterCount() == 0) {
                        plan.rollbackAttempted = true;
                        if (plan.failRollback) {
                            throw new SQLException("Forced rollback failure");
                        }
                    }

                    if (name.equals("getAutoCommit") && method.getParameterCount() == 0) {
                        if (plan.failGetAutoCommit) {
                            throw new SQLException("Forced getAutoCommit failure");
                        }
                    }

                    Object result = invoke(delegate, method, args);

                    if (name.equals("createStatement") && result instanceof Statement stmt) {
                        return statementWithFaults(stmt, plan);
                    }
                    return result;
                });
    }

    private static Statement statementWithFaults(Statement delegate, FaultPlan plan) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if (name.equals("executeQuery") && args != null && args.length > 0
                            && args[0] instanceof String sql) {

                        String normalized = sql.trim().replaceAll("\\s+", " ").toLowerCase();

                        if (normalized.contains("select sql from sqlite_master")
                                && plan.usersTableQueryReturnsNoRows) {
                            return emptyResultSet();
                        }

                        if (plan.failForeignKeyCheck
                                && sql.equalsIgnoreCase("PRAGMA foreign_key_check")) {
                            return resultSetWithRow();
                        }
                    }

                    if (name.equals("execute") && args != null && args.length > 0
                            && args[0] instanceof String sql) {

                        String normalized = sql.trim().replaceAll("\\s+", " ").toUpperCase();

                        if (plan.failMigrationInsert
                                && normalized.startsWith("INSERT INTO USERS_NEW")) {
                            throw new SQLException("Forced migration failure");
                        }

                        if (normalized.equals("PRAGMA FOREIGN_KEYS = ON")) {
                            plan.foreignKeyEnableAttempts++;
                            if (plan.failForeignKeyReenable && plan.foreignKeyEnableAttempts > 0) {
                                throw new SQLException("Forced foreign-key reenable failure");
                            }
                        }
                    }

                    return invoke(delegate, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("next")) return false;
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSetWithRow() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("next")) return true;
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    // ==================== Helpers ====================

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");
        DatabaseManager databaseManager = new DatabaseManager(database);
        databaseManager.initialize();
        return databaseManager;
    }

    private Path createLegacyDatabase(String filename) throws Exception {
        Path db = tempDir.resolve(filename);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT (
                        strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                    )
                )
                """);
            statement.execute("INSERT INTO users (username) VALUES ('alice')");
        }
        return db;
    }

    private DatabaseManager createDatabaseManagerForTest() {
        return new DatabaseManager(tempDir.resolve("reflection-test.db"));
    }
}