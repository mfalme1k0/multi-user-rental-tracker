package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DatabaseManagerHardeningTest {

    private static final String FK_ON = "PRAGMA foreign_keys = ON";

    @TempDir
    Path tempDir;

    private final List<Connection> opened = new ArrayList<>();
    private int databaseCounter;

    @AfterEach
    void closeRealConnections() {
        for (Connection connection : opened) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort: the test is over
            }
        }
    }

    // ---- fault injection --------------------------------------------------------

    @FunctionalInterface
    private interface StatementFault {
        Throwable faultFor(String sql, int occurrence);

        StatementFault NONE = (sql, occurrence) -> null;
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Statement wrap(Statement real, StatementFault fault, Map<String, Integer> seen) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("execute") && args != null && args.length == 1
                            && args[0] instanceof String sql) {
                        Throwable fail = fault.faultFor(sql, seen.merge(sql, 1, Integer::sum));
                        if (fail != null) {
                            throw fail;
                        }
                    }
                    return invoke(method, real, args);
                });
    }

    private Connection wrap(Connection real, Set<String> failing, StatementFault fault,
                            Map<String, Integer> seen) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (failing.contains(method.getName())) {
                        throw new SQLException("injected failure in " + method.getName());
                    }
                    Object result = invoke(method, real, args);
                    if (method.getName().equals("createStatement") && result instanceof Statement s) {
                        return wrap(s, fault, seen);
                    }
                    return result;
                });
    }

    private DatabaseManager managerFor(Path db, Set<String> failingConnectionMethods,
                                       StatementFault fault) {
        Map<String, Integer> seen = new HashMap<>();
        return new DatabaseManager(db, () -> {
            Connection real = DriverManager.getConnection("jdbc:sqlite:" + db);
            opened.add(real);
            return wrap(real, failingConnectionMethods, fault, seen);
        });
    }

    private DatabaseManager managerFor(Path db, String... failingConnectionMethods) {
        return managerFor(db, Set.of(failingConnectionMethods), StatementFault.NONE);
    }

    // ---- helpers ----------------------------------------------------------------

    private Path emptyDatabase() {
        return tempDir.resolve("db-" + databaseCounter++ + ".db");
    }


    private Path legacyDatabase(boolean withOrphanItem) throws SQLException {
        Path db = emptyDatabase();
        new DatabaseManager(db).initialize();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement()) {   // foreign keys are off on a raw connection
            s.execute("INSERT INTO users (username) VALUES ('owner'), ('renter')");
            s.execute("INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                    + "VALUES (1, 'Ladder', '5', 'available')");
            s.execute("INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                    + "VALUES (1, 2, '2026-10-01T10:00:00Z', '2026-10-02T10:00:00Z', 'closed')");
            if (withOrphanItem) {
                s.execute("INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                        + "VALUES (999, 'orphan', '1', 'available')");
            }
            s.execute("CREATE TABLE users_legacy (id INTEGER PRIMARY KEY, "
                    + "username TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL)");
            s.execute("INSERT INTO users_legacy SELECT id, username, created_at FROM users");
            s.execute("DROP TABLE users");
            s.execute("ALTER TABLE users_legacy RENAME TO users");
            s.execute("PRAGMA user_version = 0");
        }
        return db;
    }

    private static long scalar(Path db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.getLong(1);
        }
    }

    private static Throwable initializeFailureCause(DatabaseManager manager) {
        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::initialize);
        assertTrue(e.getMessage().contains("initialize"), e.getMessage());
        return e.getCause();
    }

    // ---- constructor ------------------------------------------------------------

    @Test
    void constructorRejectsMissingArguments() {
        DatabaseManager.ConnectionFactory factory = () -> {
            throw new SQLException("unused");
        };

        assertThrows(IllegalArgumentException.class, () -> new DatabaseManager(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseManager(null, factory));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseManager(tempDir.resolve("x.db"), null));
    }

    // ---- commit and rollback failing --------------------------------------------

    @Test
    void commitFailureIsReportedAndEndsTheTransaction() {
        DatabaseManager manager = managerFor(emptyDatabase(), "commit");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::commitTransaction);

        assertTrue(e.getMessage().contains("commit"), e.getMessage());
        assertFalse(manager.isTransactionActive());
        assertEquals(0, e.getSuppressed().length);
    }

    @Test
    void commitFailureKeepsTheCloseFailureAsSuppressed() {
        DatabaseManager manager = managerFor(emptyDatabase(), "commit", "close");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::commitTransaction);

        assertTrue(e.getMessage().contains("commit"), e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertTrue(e.getSuppressed()[0].getMessage().contains("close"));
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void rollbackThatSucceedsStillReportsAFailureToCloseTheConnection() {
        DatabaseManager manager = managerFor(emptyDatabase(), "close");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::rollbackTransaction);

        assertTrue(e.getMessage().contains("close"), e.getMessage());
        assertFalse(manager.isTransactionActive());
    }

    @Test
    void rollbackFailureKeepsTheCloseFailureAsSuppressed() {
        DatabaseManager manager = managerFor(emptyDatabase(), "rollback", "close");
        manager.beginTransaction();

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::rollbackTransaction);

        assertTrue(e.getMessage().contains("roll back"), e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertTrue(e.getSuppressed()[0].getMessage().contains("close"));
        assertFalse(manager.isTransactionActive());
    }

    // ---- creating connections ---------------------------------------------------

    @Test
    void connectionThatCannotBeConfiguredIsClosedAndReported() throws Exception {
        DatabaseManager manager = managerFor(emptyDatabase(), "createStatement");

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::getConnection);

        assertTrue(e.getMessage().contains("connect"), e.getMessage());
        assertEquals(1, opened.size());
        assertTrue(opened.get(0).isClosed(), "the half-configured connection must not leak");
        assertEquals(0, e.getCause().getSuppressed().length);
    }

    @Test
    void failureToCloseAHalfConfiguredConnectionIsKeptAsSuppressed() {
        DatabaseManager manager = managerFor(emptyDatabase(), "createStatement", "close");

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::getConnection);

        assertEquals(1, e.getCause().getSuppressed().length);
        assertTrue(e.getCause().getSuppressed()[0].getMessage().contains("close"));
    }

    @Test
    void initializeAtAFilesystemRootSkipsDirectoryCreation() {
        // A root has no parent directory to create; the failure comes later, from the connection.
        Path root = Path.of(File.listRoots()[0].getPath());
        DatabaseManager manager = new DatabaseManager(root, () -> {
            throw new SQLException("cannot open a root");
        });

        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                manager::initialize);

        assertTrue(e.getMessage().contains("connect"), e.getMessage());
    }



    // ---- the migration, failing in every place it can --------------------------


    @Test
    void migrationThatCannotStartATransactionLeavesTheDatabaseAlone() throws Exception {
        Path db = legacyDatabase(false);
        DatabaseManager manager = managerFor(db, "setAutoCommit");

        Throwable cause = initializeFailureCause(manager);

        assertTrue(cause.getMessage().contains("setAutoCommit"), cause.getMessage());
        assertEquals(0, scalar(db, "PRAGMA user_version"));
        assertEquals(2, scalar(db, "SELECT count(*) FROM users"));
    }
}