package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage-oriented tests for {@link DatabaseManager}: static helpers, transaction
 * edge cases, connection release, schema read failures, and legacy detection.
 * Complements DatabaseManagerTest / PasswordMigration / DevelopmentData tests.
 */
class DatabaseManagerCoverageTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void constructorsRejectNullPathAndFactory() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseManager(null));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseManager(null, () -> DriverManager.getConnection("jdbc:sqlite::memory:")));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseManager(tempDir.resolve("x.db"), null));
    }

    @Test
    void publicConstructorAcceptsPath() {
        Path db = tempDir.resolve("ok.db");
        DatabaseManager manager = new DatabaseManager(db);
        assertDoesNotThrow(manager::initialize);
    }

    // -------------------------------------------------------------------------
    // Static helpers (isCaseSensitive, readSchema, recordFailure)
    // -------------------------------------------------------------------------

    @Test
    void isCaseSensitiveWhenNoCollateNoCase() {
        assertTrue(DatabaseManager.isCaseSensitive(
                "CREATE TABLE users (username TEXT NOT NULL)"));
    }

    @Test
    void isCaseSensitiveFalseWhenCollateNoCasePresent() {
        assertFalse(DatabaseManager.isCaseSensitive(
                "CREATE TABLE users (username TEXT NOT NULL UNIQUE COLLATE NOCASE)"));
    }

    @Test
    void isCaseSensitiveWhenDefinitionIsNull() {
        assertTrue(DatabaseManager.isCaseSensitive(null));
    }

    @Test
    void isCaseSensitiveHandlesBlank() {
        assertDoesNotThrow(() -> DatabaseManager.isCaseSensitive(""));
        assertDoesNotThrow(() -> DatabaseManager.isCaseSensitive("CREATE TABLE t (c TEXT)"));
    }

    @Test
    void readSchemaRejectsNullStream() {
        assertThrows(DatabaseConnectionException.class, () -> DatabaseManager.readSchema(null));
    }

    @Test
    void readSchemaReadsBytes() throws Exception {
        String sql = "SELECT 1;";
        String result = DatabaseManager.readSchema(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)));
        assertEquals(sql, result);
    }

    @Test
    void recordFailureReturnsCleanupWhenOriginalIsNull() throws Exception {
        SQLException cleanup = new SQLException("cleanup");
        Throwable result = invokeRecordFailure(null, cleanup);
        assertSame(cleanup, result);
    }

    @Test
    void recordFailureSuppressesCleanupOnOriginal() throws Exception {
        SQLException original = new SQLException("original");
        SQLException cleanup = new SQLException("cleanup");
        Throwable result = invokeRecordFailure(original, cleanup);
        assertSame(original, result);
        assertTrue(Arrays.asList(original.getSuppressed()).contains(cleanup));
    }

    // -------------------------------------------------------------------------
    // usersTableIsCaseSensitive / usersTableHasPassword
    // -------------------------------------------------------------------------

    @Test
    void usersTableIsCaseSensitiveWhenCreatedWithoutNoCase() throws Exception {
        Path db = tempDir.resolve("case-sensitive-users.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL UNIQUE)");
            assertTrue(DatabaseManager.usersTableIsCaseSensitive(statement));
        }
    }

    @Test
    void usersTableIsNotCaseSensitiveWhenCollateNoCase() throws Exception {
        Path db = tempDir.resolve("case-insensitive-users.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL UNIQUE COLLATE NOCASE)");
            assertFalse(DatabaseManager.usersTableIsCaseSensitive(statement));
        }
    }

    @Test
    void usersTableHasPasswordWhenColumnPresent() throws Exception {
        Path db = tempDir.resolve("with-password.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL, "
                            + "password TEXT NOT NULL)");
            assertTrue(DatabaseManager.usersTableHasPassword(statement));
        }
    }

    @Test
    void usersTableHasPasswordFalseWhenColumnMissing() throws Exception {
        Path db = tempDir.resolve("without-password.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL)");
            assertFalse(DatabaseManager.usersTableHasPassword(statement));
        }
    }

    /**
     * Covers the try-with-resources close() failure branch (JaCoCo yellow diamond
     * on the end of usersTableHasPassword).
     */
    @Test
    void usersTableHasPasswordCoversResultSetCloseFailureWhenNoPasswordColumn() throws Exception {
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);

        when(statement.executeQuery("PRAGMA table_info(users)")).thenReturn(rows);
        when(rows.next()).thenReturn(false);
        doThrow(new SQLException("close failed")).when(rows).close();

        assertThrows(SQLException.class,
                () -> DatabaseManager.usersTableHasPassword(statement));
    }

    @Test
    void usersTableHasPasswordCoversResultSetCloseFailureAfterPasswordFound() throws Exception {
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);

        when(statement.executeQuery("PRAGMA table_info(users)")).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("name")).thenReturn("password");
        doThrow(new SQLException("close failed")).when(rows).close();

        assertThrows(SQLException.class,
                () -> DatabaseManager.usersTableHasPassword(statement));
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    @Test
    void beginTransactionTwiceThrows() {
        DatabaseManager manager = initializedManager("tx-double.db");
        manager.beginTransaction();
        try {
            assertThrows(IllegalStateException.class, manager::beginTransaction);
        } finally {
            manager.rollbackTransaction();
        }
    }

    @Test
    void commitWithoutBeginThrows() {
        DatabaseManager manager = initializedManager("tx-commit-none.db");
        assertThrows(IllegalStateException.class, manager::commitTransaction);
    }

    @Test
    void rollbackWithoutBeginThrows() {
        DatabaseManager manager = initializedManager("tx-rollback-none.db");
        assertThrows(IllegalStateException.class, manager::rollbackTransaction);
    }

    @Test
    void beginCommitClearsActiveTransaction() {
        DatabaseManager manager = initializedManager("tx-commit.db");
        manager.beginTransaction();
        assertTrue(manager.isTransactionActive());
        manager.commitTransaction();
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void beginRollbackClearsActiveTransaction() {
        DatabaseManager manager = initializedManager("tx-rollback.db");
        manager.beginTransaction();
        assertTrue(manager.isTransactionActive());
        manager.rollbackTransaction();
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void getConnectionReturnsSameConnectionDuringTransaction() throws Exception {
        DatabaseManager manager = initializedManager("tx-same-conn.db");
        manager.beginTransaction();
        try {
            Connection a = manager.getConnection();
            Connection b = manager.getConnection();
            assertSame(a, b);
        } finally {
            manager.rollbackTransaction();
        }
    }

    // -------------------------------------------------------------------------
    // releaseConnection
    // -------------------------------------------------------------------------

    @Test
    void releaseConnectionNullIsNoOp() {
        DatabaseManager manager = initializedManager("release-null.db");
        assertDoesNotThrow(() -> manager.releaseConnection(null));
    }

    @Test
    void releaseConnectionDoesNotCloseTransactionConnection() throws Exception {
        DatabaseManager manager = initializedManager("release-tx.db");
        manager.beginTransaction();
        try {
            Connection tx = manager.getConnection();
            manager.releaseConnection(tx);
            assertFalse(tx.isClosed());
            assertTrue(manager.isTransactionActive());
        } finally {
            manager.rollbackTransaction();
        }
    }

    @Test
    void releaseConnectionClosesNonTransactionConnection() throws Exception {
        DatabaseManager manager = initializedManager("release-close.db");
        Connection connection = manager.getConnection();
        manager.releaseConnection(connection);
        assertTrue(connection.isClosed());
    }

    // -------------------------------------------------------------------------
    // initialize + development data
    // -------------------------------------------------------------------------

    @Test
    void initializeCreatesSchemaAndVersion() throws Exception {
        DatabaseManager manager = initializedManager("init-schema.db");
        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(version.next());
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
        }
    }

    @Test
    void initializeIsIdempotentWhenSchemaAlreadyCurrent() {
        Path db = tempDir.resolve("already-current.db");
        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();
        assertDoesNotThrow(manager::initialize); // version < SCHEMA_VERSION is false
    }

    @Test
    void initializeDevelopmentDataIsIdempotent() throws Exception {
        Path db = tempDir.resolve("seed.db");
        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();
        manager.initializeDevelopmentData();
        manager.initializeDevelopmentData();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet users = statement.executeQuery("SELECT count(*) FROM users")) {
            assertTrue(users.next());
            assertTrue(users.getLong(1) >= 2);
        }
    }

    @Test
    void initializeUpgradesLegacyUsersWithoutPassword() throws Exception {
        Path db = tempDir.resolve("legacy-no-password.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL UNIQUE)");
            statement.execute("INSERT INTO users (id, username) VALUES (1, 'old')");
            statement.execute("PRAGMA user_version = 1");
        }

        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(DatabaseManager.SCHEMA_VERSION, pragmaUserVersion(statement));
            assertTrue(DatabaseManager.usersTableHasPassword(statement));
            assertEquals(0, scalar(statement, "SELECT count(*) FROM users"));
        }
    }

    @Test
    void initializeDoesNotResetCaseSensitiveUsersWithValidPasswords() throws Exception {
        Path db = tempDir.resolve("legacy-case-sensitive.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE users ("
                            + "id INTEGER PRIMARY KEY, "
                            + "username TEXT NOT NULL UNIQUE, "
                            + "password TEXT NOT NULL)");
            statement.execute(
                    "INSERT INTO users (id, username, password) VALUES (1, 'OldUser', 'x')");
            statement.execute("PRAGMA user_version = 1");
        }

        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(DatabaseManager.SCHEMA_VERSION, pragmaUserVersion(statement));
            assertTrue(DatabaseManager.usersTableIsCaseSensitive(statement));
            assertEquals(1, scalar(statement, "SELECT count(*) FROM users"));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DatabaseManager initializedManager(String fileName) {
        Path db = tempDir.resolve(fileName);
        DatabaseManager manager = new DatabaseManager(db);
        manager.initialize();
        return manager;
    }

    private static long scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static int pragmaUserVersion(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static Throwable invokeRecordFailure(
            Throwable original, SQLException cleanup) throws Exception {
        Method method = DatabaseManager.class.getDeclaredMethod(
                "recordFailure", Throwable.class, SQLException.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, original, cleanup);
    }
}