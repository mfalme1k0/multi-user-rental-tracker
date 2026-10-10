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

    @Test
    void migrationReportsUserVersionResultSetCloseFailure() throws Exception {
        Path db = createLegacyDatabase("user-version-resultset-close.db");

        FaultPlan plan = new FaultPlan();
        plan.failUserVersionResultSetClose = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Forced user_version ResultSet close failure",
                exception.getCause().getMessage()
        );
    }

    @Test
    void migrationReportsUserVersionStatementCloseFailure() throws Exception {
        Path db = createLegacyDatabase("user-version-statement-close.db");

        FaultPlan plan = new FaultPlan();
        plan.failUserVersionStatementClose = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Forced user_version Statement close failure",
                exception.getCause().getMessage()
        );
    }



    @Test
    void initializeUpdatesVersionWhenUsersTableAlreadyUsesNoCase()
            throws Exception {

        Path db = tempDir.resolve("already-no-case.db");

        try (Connection connection =
                     DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    created_at TEXT NOT NULL DEFAULT (
                        strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                    )
                )
                """);

            statement.execute("""
                INSERT INTO users (username)
                VALUES ('alice')
                """);

            statement.execute("PRAGMA user_version = 0");
        }

        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery("PRAGMA user_version")) {

            assertTrue(resultSet.next());
            assertEquals(DatabaseManager.SCHEMA_VERSION, resultSet.getInt(1));
        }

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {

            assertThrows(SQLException.class, () ->
                    statement.executeUpdate("""
                        INSERT INTO users (username)
                        VALUES ('ALICE')
                        """));
        }
    }

    @Test
    void commitTransactionRemovesTransactionWhenConnectionCloseFails()
            throws Exception {

        Path db = tempDir.resolve("close-failure.db");

        // Initialize the database normally before injecting the failure.
        new DatabaseManager(db).initialize();

        DatabaseManager manager = new DatabaseManager(
                db,
                () -> connectionThatFailsOnClose(
                        DriverManager.getConnection("jdbc:sqlite:" + db)
                )
        );

        manager.beginTransaction();

        assertTrue(manager.isTransactionActive());

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::commitTransaction
        );

        assertEquals(
                "Could not close transaction connection",
                exception.getMessage()
        );

        assertFalse(manager.isTransactionActive());
    }


    private static Connection connectionThatFailsOnClose(
            Connection delegate
    ) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")
                            && method.getParameterCount() == 0) {

                        delegate.close();

                        throw new SQLException("Forced close failure");
                    }

                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
    }


    @Test
    void migrationFailurePreservesOriginalExceptionWhenRollbackSucceeds()
            throws Exception {

        Path db = createLegacyDatabase("migration-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failMigrationInsert = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Forced migration failure",
                exception.getCause().getMessage()
        );

        assertEquals(0, exception.getCause().getSuppressed().length);
        assertTrue(plan.rollbackAttempted);
    }

    @Test
    void migrationFailureSuppressesRollbackFailure()
            throws Exception {

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
        assertEquals(
                "Forced rollback failure",
                original.getSuppressed()[0].getMessage()
        );

        assertTrue(plan.rollbackAttempted);
        assertFalse(plan.autoCommitReadAttempted);
    }

    @Test
    void cleanupFailureBecomesPrimaryFailureWhenMigrationSucceeded()
            throws Exception {

        Path db = createLegacyDatabase("autocommit-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failGetAutoCommit = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Forced getAutoCommit failure",
                exception.getCause().getMessage()
        );
        assertEquals(0, exception.getCause().getSuppressed().length);
    }

    @Test
    void cleanupFailureIsSuppressedWhenMigrationAlreadyFailed()
            throws Exception {

        Path db = createLegacyDatabase("combined-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failMigrationInsert = true;
        plan.failGetAutoCommit = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        Throwable original = exception.getCause();

        assertEquals("Forced migration failure", original.getMessage());
        assertEquals(1, original.getSuppressed().length);
        assertEquals(
                "Forced getAutoCommit failure",
                original.getSuppressed()[0].getMessage()
        );
    }

    @Test
    void foreignKeyReenableFailureIsReportedAfterSuccessfulMigration()
            throws Exception {

        Path db = createLegacyDatabase("foreign-key-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failForeignKeyReenable = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Forced foreign-key reenable failure",
                exception.getCause().getMessage()
        );
    }



//legacy db for test cases
    private Path createLegacyDatabase(String filename) throws Exception {
        Path db = tempDir.resolve(filename);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db);
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

            statement.execute("""
                INSERT INTO users (username)
                VALUES ('alice')
                """);
        }

        return db;
    }


    @Test
    void migrationFailsWhenUsersTableCannotBeInspected()
            throws Exception {

        Path db = createLegacyDatabase("missing-inspection-row.db");
        FaultPlan plan = new FaultPlan();
        plan.usersTableQueryReturnsNoRows = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::initialize
        );

        assertEquals(
                "Cannot inspect users table: table does not exist",
                exception.getCause().getMessage()
        );
    }

    @Test
    void migrationRebuildsWhenUsersTableDefinitionIsNull()
            throws Exception {

        Path db = createLegacyDatabase("null-table-definition.db");
        FaultPlan plan = new FaultPlan();
        plan.usersTableDefinitionIsNull = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        assertDoesNotThrow(manager::initialize);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {

            SQLException duplicate = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                        INSERT INTO users (username)
                        VALUES ('ALICE')
                        """)
            );

            assertTrue(
                    duplicate.getMessage().contains("UNIQUE constraint failed")
            );
        }
    }

    @Test
    void closeTransactionConnectionDoesNothingWithoutActiveConnection()
            throws Exception {

        DatabaseManager manager = createDatabaseManagerForTest();

        Method method = DatabaseManager.class.getDeclaredMethod(
                "closeTransactionConnection"
        );
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(manager));
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void commitTransactionClearsStateWhenConnectionCloseFails()
            throws Exception {

        Path db = tempDir.resolve("transaction-close-failure.db");
        FaultPlan plan = new FaultPlan();
        plan.failConnectionClose = true;

        DatabaseManager manager = managerWithFaults(db, plan);

        manager.beginTransaction();

        DatabaseConnectionException exception = assertThrows(
                DatabaseConnectionException.class,
                manager::commitTransaction
        );

        assertEquals(
                "Could not close transaction connection",
                exception.getMessage()
        );
        assertNotNull(exception.getCause());
        assertEquals(
                "Forced connection close failure",
                exception.getCause().getMessage()
        );
        assertFalse(manager.isTransactionActive());
    }
    @Test
    void migrationRethrowsRuntimeExceptionAfterSuccessfulRollback() throws Exception {
        Path databasePath = tempDir.resolve("runtime-migration.db");
        createLegacyDatabase("runtime-migration.db");

        FaultPlan plan = new FaultPlan();
        plan.failMigrationWithRuntimeException = true;

        DatabaseManager databaseManager = managerWithFaults(databasePath, plan);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                databaseManager::initialize
        );

        assertEquals("Forced migration runtime failure", exception.getMessage());
        assertTrue(plan.rollbackAttempted);
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

    @Test
    void initializeDoesNotUpgradeWhenSchemaVersionIsCurrent() throws Exception {
        DatabaseManager databaseManager = createDatabase();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 999");
        }

        assertDoesNotThrow(databaseManager::initialize);

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(resultSet.next());
            assertEquals(999, resultSet.getInt(1));
        }
    }



    //helpers for testing purposes

    private static final class FaultPlan {
        private boolean failMigrationInsert;
        private boolean failRollback;
        private boolean failGetAutoCommit;
        private boolean failForeignKeyReenable;
        private boolean failConnectionClose;
        private boolean failMigrationWithRuntimeException;
        private boolean failUserVersionResultSetClose;
        private boolean failUserVersionStatementClose;

        private boolean usersTableQueryReturnsNoRows;
        private boolean usersTableDefinitionIsNull;

        private boolean rollbackAttempted;
        private boolean autoCommitReadAttempted;

        private int foreignKeyEnableAttempts;
    }


    private DatabaseManager managerWithFaults(Path db, FaultPlan plan) {
        return new DatabaseManager(
                db,
                () -> connectionWithFaults(
                        DriverManager.getConnection("jdbc:sqlite:" + db),
                        plan
                )
        );
    }


    private static Connection connectionWithFaults(
            Connection delegate,
            FaultPlan plan
    ) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();

                    // Must be checked independently of getAutoCommit().
                    if (methodName.equals("close")
                            && method.getParameterCount() == 0
                            && plan.failConnectionClose) {
                        throw new SQLException(
                                "Forced connection close failure"
                        );
                    }

                    if (methodName.equals("rollback")
                            && method.getParameterCount() == 0) {
                        plan.rollbackAttempted = true;

                        if (plan.failRollback) {
                            throw new SQLException(
                                    "Forced rollback failure"
                            );
                        }
                    }

                    if (methodName.equals("getAutoCommit")
                            && method.getParameterCount() == 0) {
                        plan.autoCommitReadAttempted = true;

                        if (plan.failGetAutoCommit) {
                            throw new SQLException(
                                    "Forced getAutoCommit failure"
                            );
                        }
                    }

                    Object result = invokeDelegate(delegate, method, args);

                    if (methodName.equals("createStatement")
                            && result instanceof Statement statement) {
                        return statementWithFaults(statement, plan);
                    }

                    return result;
                }
        );
    }


    private static Statement statementWithFaults(
            Statement delegate,
            FaultPlan plan
    ) {
        boolean[] userVersionQuery = {false};

        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {

                    String methodName = method.getName();

                    if (methodName.equals("close")
                            && method.getParameterCount() == 0
                            && userVersionQuery[0]
                            && plan.failUserVersionStatementClose) {
                        throw new SQLException(
                                "Forced user_version Statement close failure"
                        );
                    }

                    if (methodName.equals("executeQuery")
                            && args != null
                            && args.length > 0
                            && args[0] instanceof String query) {

                        String normalizedQuery = query
                                .trim()
                                .replaceAll("\\s+", " ")
                                .toLowerCase(java.util.Locale.ROOT);

                        if (normalizedQuery.equals("pragma user_version")) {
                            userVersionQuery[0] = true;

                            ResultSet resultSet = (ResultSet)
                                    invokeDelegate(delegate, method, args);

                            return resultSetWithFaults(resultSet, plan);
                        }

                        if (normalizedQuery.contains(
                                "select sql from sqlite_master")) {

                            if (plan.usersTableQueryReturnsNoRows) {
                                return fakeResultSet(false, null);
                            }

                            if (plan.usersTableDefinitionIsNull) {
                                return fakeResultSet(true, null);
                            }
                        }
                    }

                    if (methodName.equals("execute")
                            && args != null
                            && args.length > 0
                            && args[0] instanceof String sql) {

                        String normalized = sql
                                .trim()
                                .replaceAll("\\s+", " ")
                                .toUpperCase(java.util.Locale.ROOT);

                        if (plan.failMigrationInsert
                                && normalized.startsWith("INSERT INTO USERS_NEW")) {
                            throw new SQLException("Forced migration failure");
                        }

                        if (plan.failMigrationWithRuntimeException
                                && normalized.startsWith("INSERT INTO USERS_NEW")) {
                            throw new IllegalStateException(
                                    "Forced migration runtime failure"
                            );
                        }

                        if (normalized.equals("PRAGMA FOREIGN_KEYS = ON")) {
                            plan.foreignKeyEnableAttempts++;

                            if (plan.failForeignKeyReenable
                                    && plan.foreignKeyEnableAttempts > 1) {
                                throw new SQLException(
                                        "Forced foreign-key reenable failure"
                                );
                            }
                        }
                    }

                    return invokeDelegate(delegate, method, args);
                }
        );
    }

    private static ResultSet resultSetWithFaults(
            ResultSet delegate,
            FaultPlan plan
    ) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")
                            && method.getParameterCount() == 0
                            && plan.failUserVersionResultSetClose) {
                        throw new SQLException(
                                "Forced user_version ResultSet close failure"
                        );
                    }

                    return invokeDelegate(delegate, method, args);
                }
        );
    }



    private static Object invokeDelegate(
            Object delegate,
            java.lang.reflect.Method method,
            Object[] args
    ) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private DatabaseManager createDatabaseManagerForTest() {
        return new DatabaseManager(tempDir.resolve("reflection-test.db"));
    }
    private static ResultSet fakeResultSet(
            boolean hasRow,
            String tableDefinition
    ) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> hasRow;
                    case "getString" -> tableDefinition;
                    case "wasNull" -> tableDefinition == null;
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> {
                        Class<?> type = method.getReturnType();

                        if (!type.isPrimitive() || type == void.class) {
                            yield null;
                        }
                        if (type == boolean.class) yield false;
                        if (type == int.class) yield 0;
                        if (type == long.class) yield 0L;
                        if (type == double.class) yield 0.0;
                        if (type == float.class) yield 0.0f;
                        if (type == short.class) yield (short) 0;
                        if (type == byte.class) yield (byte) 0;
                        if (type == char.class) yield '\0';

                        yield null;
                    }
                }
        );
    }

}