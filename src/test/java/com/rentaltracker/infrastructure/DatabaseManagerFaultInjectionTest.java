package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class DatabaseManagerFaultInjectionTest {

    private static final Path DB = Path.of("fault-injection.db");

    @TempDir
    Path tempDir;

    private Connection connection;
    private Statement statement;
    private DatabaseManager manager;

    @BeforeEach
    void setUp() throws Exception {
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        manager = new DatabaseManager(DB, () -> connection);
    }

    /** Stubs what initialize() reads: PRAGMA user_version, then the users table columns. */
    private void stubMigrationQueries(int userVersion, boolean usersHasPassword) throws Exception {
        ResultSet version = mock(ResultSet.class);
        when(version.getInt(1)).thenReturn(userVersion);
        when(statement.executeQuery("PRAGMA user_version")).thenReturn(version);

        ResultSet columns = mock(ResultSet.class);
        when(columns.next()).thenReturn(usersHasPassword, false);
        when(columns.getString("name")).thenReturn("password");
        when(statement.executeQuery("PRAGMA table_info(users)")).thenReturn(columns);

        ResultSet emptyPasswords = mock(ResultSet.class);
        when(emptyPasswords.next()).thenReturn(false);
        when(statement.executeQuery(
                "SELECT 1 FROM users WHERE password IS NULL OR trim(password) = '' LIMIT 1"))
                .thenReturn(emptyPasswords);
    }

    // ==================== connecting ====================

    @Test
    void getConnectionWrapsAFactoryFailure() {
        DatabaseManager failing = new DatabaseManager(DB, () -> {
            throw new SQLException("cannot open");
        });

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, failing::getConnection);

        assertEquals("Could not connect to database", e.getMessage());
        assertEquals("cannot open", e.getCause().getMessage());
    }

    @Test
    void getConnectionClosesTheConnectionWhenThePragmaFails() throws Exception {
        doThrow(new SQLException("pragma failed")).when(statement).execute("PRAGMA foreign_keys = ON");

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::getConnection);

        assertEquals("pragma failed", e.getCause().getMessage());
        verify(connection).close();
    }

    @Test
    void getConnectionKeepsTheCloseFailureAsSuppressedWhenThePragmaFails() throws Exception {
        doThrow(new SQLException("pragma failed")).when(statement).execute("PRAGMA foreign_keys = ON");
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::getConnection);

        assertEquals(1, e.getCause().getSuppressed().length);
        assertEquals("close failed", e.getCause().getSuppressed()[0].getMessage());
    }

    // ==================== releasing ====================

    @Test
    void releaseConnectionWrapsACloseFailure() throws Exception {
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> manager.releaseConnection(connection));

        assertEquals("Could not close database connection", e.getMessage());
    }

    @Test
    void releaseConnectionLeavesTheTransactionConnectionOpenButClosesOthers() throws Exception {
        Connection other = mock(Connection.class);
        manager.beginTransaction();

        manager.releaseConnection(connection);
        manager.releaseConnection(other);

        verify(connection, never()).close();
        verify(other).close();
        manager.rollbackTransaction();
    }

    // ==================== beginTransaction ====================

    @Test
    void beginTransactionClosesTheConnectionWhenAutoCommitCannotBeDisabled() throws Exception {
        doThrow(new SQLException("no autocommit")).when(connection).setAutoCommit(false);

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::beginTransaction);

        assertEquals("Could not begin database transaction", e.getMessage());
        assertFalse(manager.isTransactionActive());
        verify(connection).close();
    }

    @Test
    void beginTransactionKeepsTheCloseFailureAsSuppressed() throws Exception {
        doThrow(new SQLException("no autocommit")).when(connection).setAutoCommit(false);
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::beginTransaction);

        assertEquals(1, e.getCause().getSuppressed().length);
        assertFalse(manager.isTransactionActive());
    }

    // ==================== commit ====================

    @Test
    void commitFailureIsWrappedAndTheTransactionIsStillCleanedUp() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("commit failed")).when(connection).commit();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::commitTransaction);

        assertEquals("Could not commit database transaction", e.getMessage());
        assertEquals(0, e.getSuppressed().length);
        assertFalse(manager.isTransactionActive());
        verify(connection).close();
    }

    @Test
    void closeFailureAfterASuccessfulCommitIsReported() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::commitTransaction);

        assertEquals("Could not close transaction connection", e.getMessage());
        verify(connection).commit();
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void closeFailureAfterACommitFailureIsSuppressedUnderTheCommitFailure() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("commit failed")).when(connection).commit();
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::commitTransaction);

        assertEquals("Could not commit database transaction", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("Could not close transaction connection", e.getSuppressed()[0].getMessage());
        assertFalse(manager.isTransactionActive());
    }

    // ==================== rollback ====================

    @Test
    void rollbackFailureIsWrappedAndTheTransactionIsStillCleanedUp() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("rollback failed")).when(connection).rollback();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::rollbackTransaction);

        assertEquals("Could not roll back database transaction", e.getMessage());
        assertEquals(0, e.getSuppressed().length);
        assertFalse(manager.isTransactionActive());
        verify(connection).close();
    }

    @Test
    void closeFailureAfterASuccessfulRollbackIsReported() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::rollbackTransaction);

        assertEquals("Could not close transaction connection", e.getMessage());
        verify(connection).rollback();
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void closeFailureAfterARollbackFailureIsSuppressedUnderTheRollbackFailure() throws Exception {
        manager.beginTransaction();
        doThrow(new SQLException("rollback failed")).when(connection).rollback();
        doThrow(new SQLException("close failed")).when(connection).close();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::rollbackTransaction);

        assertEquals("Could not roll back database transaction", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertFalse(manager.isTransactionActive());
    }

    // ==================== initialize ====================

    @Test
    void initializeReportsAnUncreatableDatabaseDirectory() throws Exception {
        Path aFile = tempDir.resolve("plain-file");
        Files.writeString(aFile, "not a directory");
        DatabaseManager blocked = new DatabaseManager(aFile.resolve("sub").resolve("app.db"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, blocked::initialize);

        assertEquals("Could not create database directory", e.getMessage());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void initializeSkipsDirectoryCreationForAPathWithoutAParent() throws Exception {
        stubMigrationQueries(DatabaseManager.SCHEMA_VERSION, true);
        DatabaseManager rootPath = new DatabaseManager(Path.of("/"), () -> connection);

        rootPath.initialize();

        verify(statement).executeUpdate(anyString());
    }

    @Test
    void initializeWrapsASqlFailure() throws Exception {
        when(statement.executeUpdate(anyString())).thenThrow(new SQLException("bad schema"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("Could not initialize database", e.getMessage());
        assertEquals("bad schema", e.getCause().getMessage());
    }

    @Test
    void initializeDevelopmentDataWrapsASqlFailure() throws Exception {
        when(statement.executeUpdate(anyString())).thenThrow(new SQLException("bad seed"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initializeDevelopmentData);

        assertEquals("Could not initialize development data", e.getMessage());
        assertEquals("bad seed", e.getCause().getMessage());
    }

    @Test
    void readSchemaRejectsAMissingResource() {
        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, () -> DatabaseManager.readSchema(null));

        assertEquals("schema.sql not found", e.getMessage());
    }

    @Test
    void readSchemaReadsUtf8Text() throws Exception {
        InputStream input = new java.io.ByteArrayInputStream("CREATE TABLE t (é);".getBytes(StandardCharsets.UTF_8));

        assertEquals("CREATE TABLE t (é);", DatabaseManager.readSchema(input));
    }

    @Test
    void readSchemaPropagatesAReadFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable");
            }
        };

        assertThrows(IOException.class, () -> DatabaseManager.readSchema(broken));
    }

    // ==================== migration: upgrade without a reset ====================

    @Test
    void olderSchemaWithValidUsersIsUpgradedWithoutLosingData() throws Exception {
        Path database = tempDir.resolve("upgrade.db");
        DatabaseManager real = new DatabaseManager(database);
        real.initialize();
        try (Connection c = real.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (username, password) VALUES ('keeper', 'real-hash')");
            s.execute("PRAGMA user_version = 2");
        }

        real.initialize();

        try (Connection c = real.getConnection();
             Statement s = c.createStatement();
             ResultSet users = s.executeQuery("SELECT count(*) FROM users WHERE username = 'keeper'")) {
            assertTrue(users.next());
            assertEquals(1, users.getInt(1));
        }
        try (Connection c = real.getConnection();
             Statement s = c.createStatement();
             ResultSet version = s.executeQuery("PRAGMA user_version")) {
            assertTrue(version.next());
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
        }
    }

    // ==================== case-sensitivity helpers ====================

    @Test
    void isCaseSensitiveLooksForNocaseCollation() {
        assertTrue(DatabaseManager.isCaseSensitive(null));
        assertTrue(DatabaseManager.isCaseSensitive("CREATE TABLE users (username TEXT)"));
        assertFalse(DatabaseManager.isCaseSensitive("CREATE TABLE users (username TEXT COLLATE NOCASE)"));
        assertFalse(DatabaseManager.isCaseSensitive("create table users (username text collate nocase)"));
    }

    @Test
    void usersTableIsCaseSensitiveReadsTheStoredDefinition() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("cs.db"));
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (username TEXT)");
            assertTrue(DatabaseManager.usersTableIsCaseSensitive(s));
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("ci.db"));
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (username TEXT COLLATE NOCASE)");
            assertFalse(DatabaseManager.usersTableIsCaseSensitive(s));
        }
    }

    @Test
    void usersTableIsCaseSensitiveRejectsAMissingTable() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("empty.db"));
             Statement s = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () -> DatabaseManager.usersTableIsCaseSensitive(s));

            assertTrue(e.getMessage().contains("table does not exist"), e.getMessage());
        }
    }

    // ==================== legacy reset: success and failure ====================

    @Test
    void legacyResetSucceedsAndRestoresAutoCommit() throws Exception {
        stubMigrationQueries(0, false);

        manager.initialize();

        verify(connection).commit();
        verify(connection).setAutoCommit(true);
        verify(statement).execute("PRAGMA user_version = " + DatabaseManager.SCHEMA_VERSION);
        verify(statement, times(2)).execute("PRAGMA foreign_keys = ON");
    }

    @Test
    void legacyResetSkipsRestoringAutoCommitWhenItIsAlreadyOn() throws Exception {
        stubMigrationQueries(0, false);
        when(connection.getAutoCommit()).thenReturn(true);

        manager.initialize();

        verify(connection, never()).setAutoCommit(true);
        verify(statement, times(2)).execute("PRAGMA foreign_keys = ON");
    }

    @Test
    void legacyResetRollsBackAndReportsASqlFailure() throws Exception {
        stubMigrationQueries(0, false);
        when(statement.executeUpdate(anyString())).thenReturn(0).thenThrow(new SQLException("reset failed"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("Could not initialize database", e.getMessage());
        assertEquals("reset failed", e.getCause().getMessage());
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void legacyResetRollsBackAndRethrowsARuntimeFailure() throws Exception {
        stubMigrationQueries(0, false);
        IllegalStateException failure = new IllegalStateException("runtime failure");
        when(statement.executeUpdate(anyString())).thenReturn(0).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::initialize);

        assertSame(failure, thrown);
        verify(connection).rollback();
    }

    @Test
    void legacyResetSkipsCleanupWhenRollbackFails() throws Exception {
        stubMigrationQueries(0, false);
        when(statement.executeUpdate(anyString())).thenReturn(0).thenThrow(new SQLException("reset failed"));
        doThrow(new SQLException("rollback failed")).when(connection).rollback();

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("reset failed", e.getCause().getMessage());
        assertEquals(1, e.getCause().getSuppressed().length);
        assertEquals("rollback failed", e.getCause().getSuppressed()[0].getMessage());
        verify(connection, never()).setAutoCommit(true);
        verify(statement, times(1)).execute("PRAGMA foreign_keys = ON");
    }

    @Test
    void legacyResetThatFailsBeforeTheTransactionStartsDoesNotRollBack() throws Exception {
        stubMigrationQueries(0, false);
        doThrow(new SQLException("fk off failed")).when(statement).execute("PRAGMA foreign_keys = OFF");

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("fk off failed", e.getCause().getMessage());
        verify(connection, never()).rollback();
    }

    @Test
    void legacyResetReportsAFailureToRestoreAutoCommit() throws Exception {
        stubMigrationQueries(0, false);
        doThrow(new SQLException("autocommit failed")).when(connection).setAutoCommit(true);

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("autocommit failed", e.getCause().getMessage());
        // Foreign keys are only re-enabled after auto-commit is back: just the connection setup ran.
        verify(statement, times(1)).execute("PRAGMA foreign_keys = ON");
    }

    @Test
    void legacyResetReportsAFailureToReenableForeignKeys() throws Exception {
        stubMigrationQueries(0, false);
        when(statement.execute("PRAGMA foreign_keys = ON")).thenReturn(false).thenThrow(new SQLException("fk on failed"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("fk on failed", e.getCause().getMessage());
    }

    @Test
    void legacyResetKeepsTheOriginalFailureWhenCleanupAlsoFails() throws Exception {
        stubMigrationQueries(0, false);
        when(statement.executeUpdate(anyString())).thenReturn(0).thenThrow(new SQLException("reset failed"));
        when(statement.execute("PRAGMA foreign_keys = ON")).thenReturn(false).thenThrow(new SQLException("fk on failed"));

        DatabaseConnectionException e =
                assertThrows(DatabaseConnectionException.class, manager::initialize);

        assertEquals("reset failed", e.getCause().getMessage());
        assertEquals(1, e.getCause().getSuppressed().length);
        assertEquals("fk on failed", e.getCause().getSuppressed()[0].getMessage());
    }
}
