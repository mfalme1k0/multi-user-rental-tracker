package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DatabaseManagerFailureTest {

    @TempDir
    Path tempDir;

    /** Opened connections, so each test can close the real ones the proxy refuses to close. */
    private final List<Connection> opened = new ArrayList<>();

    private DatabaseManager managerWhoseConnectionsFail(String... failingMethods) {
        Path db = tempDir.resolve("proxy.db");
        Set<String> failing = Set.of(failingMethods);
        return new DatabaseManager(db, () -> {
            Connection real = DriverManager.getConnection("jdbc:sqlite:" + db);
            opened.add(real);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (failing.contains(method.getName())) {
                            throw new SQLException("injected failure in " + method.getName());
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        });
    }

    private void closeRealConnections() throws SQLException {
        for (Connection connection : opened) {
            connection.close();
        }
    }

    // ---- releaseConnection ------------------------------------------------------

    @Test
    void releaseConnectionIgnoresNull() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("a.db"));

        manager.releaseConnection(null);   // must not throw
    }

    @Test
    void releaseConnectionReportsCloseFailure() throws Exception {
        DatabaseManager manager = managerWhoseConnectionsFail("close");
        Connection connection = manager.getConnection();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> manager.releaseConnection(connection));

        assertTrue(e.getMessage().contains("close"), e.getMessage());
        assertInstanceOf(SQLException.class, e.getCause());
        closeRealConnections();
    }

    // ---- transaction lifecycle misuse ------------------------------------------

    @Test
    void beginTransactionTwiceIsRejected() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("b.db"));
        manager.beginTransaction();
        try {
            assertThrows(IllegalStateException.class, manager::beginTransaction);
            assertTrue(manager.isTransactionActive());      // the first one is untouched
        } finally {
            manager.rollbackTransaction();
        }
    }

    @Test
    void commitWithoutTransactionIsRejected() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("c.db"));

        assertThrows(IllegalStateException.class, manager::commitTransaction);
    }

    @Test
    void rollbackWithoutTransactionIsRejected() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("d.db"));

        assertThrows(IllegalStateException.class, manager::rollbackTransaction);
    }

    // ---- begin / commit / rollback failing --------------------------------------

    @Test
    void beginTransactionReportsSetAutoCommitFailureAndStaysInactive() throws Exception {
        DatabaseManager manager = managerWhoseConnectionsFail("setAutoCommit");

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::beginTransaction);

        assertTrue(e.getMessage().contains("begin"), e.getMessage());
        assertFalse(manager.isTransactionActive());
        assertEquals(0, e.getCause().getSuppressed().length);
        closeRealConnections();
    }

    @Test
    void beginTransactionKeepsTheCloseFailureAsSuppressed() throws Exception {
        DatabaseManager manager = managerWhoseConnectionsFail("setAutoCommit", "close");

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::beginTransaction);

        assertFalse(manager.isTransactionActive());
        assertEquals(1, e.getCause().getSuppressed().length);
        closeRealConnections();
    }

    @Test
    void rollbackFailureIsReportedAndStillEndsTheTransaction() throws Exception {
        DatabaseManager manager = managerWhoseConnectionsFail("rollback");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::rollbackTransaction);

        assertTrue(e.getMessage().contains("roll back"), e.getMessage());
        assertFalse(manager.isTransactionActive());
        closeRealConnections();
    }

    @Test
    void commitReportsFailureToCloseTheTransactionConnection() throws Exception {
        DatabaseManager manager = managerWhoseConnectionsFail("close");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::commitTransaction);

        assertTrue(e.getMessage().contains("close"), e.getMessage());
        assertFalse(manager.isTransactionActive());     // unbound even though close failed
        closeRealConnections();
    }

    // ---- connecting -------------------------------------------------------------

    @Test
    void getConnectionReportsFailureToOpen() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("e.db"), () -> {
            throw new SQLException("cannot open");
        });

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::getConnection);

        assertTrue(e.getMessage().contains("connect"), e.getMessage());
        assertEquals("cannot open", e.getCause().getMessage());
    }

    @Test
    void getConnectionReportsAPathSqliteCannotOpen() {
        // A directory is not a database file.
        DatabaseManager manager = new DatabaseManager(tempDir);

        assertThrows(DatabaseConnectionException.class, manager::getConnection);
    }

    // ---- initialize -------------------------------------------------------------

    @Test
    void initializeReportsADirectoryItCannotCreate() throws Exception {
        Path regularFile = Files.writeString(tempDir.resolve("not-a-dir"), "x");
        DatabaseManager manager = new DatabaseManager(regularFile.resolve("sub").resolve("app.db"));

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::initialize);

        assertTrue(e.getMessage().contains("directory"), e.getMessage());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void initializeReportsAFileThatIsNotADatabase() throws Exception {
        Path db = tempDir.resolve("garbage.db");
        Files.write(db, "this is definitely not an sqlite file, ".repeat(50)
                .getBytes(StandardCharsets.UTF_8));

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> new DatabaseManager(db).initialize());

        assertTrue(e.getMessage().contains("initialize"), e.getMessage());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    @Test
    void readSchemaRejectsAMissingResource() {
        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> DatabaseManager.readSchema(null));

        assertTrue(e.getMessage().contains("schema.sql"), e.getMessage());
    }

    @Test
    void readSchemaPassesOnAnUnreadableStream() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };

        IOException e = assertThrows(IOException.class, () -> DatabaseManager.readSchema(broken));

        assertEquals("disk gone", e.getMessage());
    }

    @Test
    void readSchemaDecodesUtf8() throws Exception {
        InputStream input = new ByteArrayInputStream("CREATE TABLE t(é);".getBytes(StandardCharsets.UTF_8));

        assertEquals("CREATE TABLE t(é);", DatabaseManager.readSchema(input));
    }

    // ---- migration safety net ---------------------------------------------------

    @Test
    void failedMigrationRollsBackAndLeavesTheLegacyTableUntouched() throws Exception {
        Path db = tempDir.resolve("dangling.db");
        new DatabaseManager(db).initialize();           // create the current schema first

        // Turn it into a legacy database: case-sensitive users table, version 0,
        // and an item whose owner does not exist (foreign keys are off on a raw connection).
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            s.execute("DROP TABLE rentals");
            s.execute("DROP TABLE listed_items");
            s.execute("DROP TABLE users");
            s.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, "
                    + "created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))");
            s.execute("CREATE TABLE listed_items (id INTEGER PRIMARY KEY, owner_id INTEGER NOT NULL, "
                    + "name TEXT NOT NULL, description TEXT, cost_per_day TEXT NOT NULL, "
                    + "status TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT 'x', "
                    + "FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT)");
            s.execute("INSERT INTO users (username) VALUES ('owner')");
            s.execute("INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                    + "VALUES (999, 'orphan', '1', 'available')");
            s.execute("PRAGMA user_version = 0");
        }

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> new DatabaseManager(db).initialize());

        assertTrue(e.getCause().getMessage().contains("dangling foreign keys"), e.getCause().getMessage());

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA user_version")) {
                assertEquals(0, rs.getInt(1));                      // not marked as migrated
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT sql FROM sqlite_master WHERE name = 'users'")) {
                assertFalse(rs.getString(1).toUpperCase().contains("COLLATE NOCASE"));
            }
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM users")) {
                assertEquals(1, rs.getInt(1));                      // data survived the rollback
            }
        }
    }
}