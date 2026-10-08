package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class DatabaseManager {

    private final Path databasePath;


    private final ThreadLocal<Connection> transactionConnection =
            new ThreadLocal<>();

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
    }

    public Connection getConnection() {
        Connection activeConnection = transactionConnection.get();

        if (activeConnection != null) {
            return activeConnection;
        }

        return createConnection();
    }


    public void releaseConnection(Connection connection) {
        if (connection == null) {
            return;
        }

        if (connection == transactionConnection.get()) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not close database connection",
                    e
            );
        }
    }


    public void beginTransaction() {
        if (transactionConnection.get() != null) {
            throw new IllegalStateException(
                    "A transaction is already active"
            );
        }

        Connection connection = createConnection();

        try {
            connection.setAutoCommit(false);
            transactionConnection.set(connection);
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                e.addSuppressed(closeException);
            }

            throw new DatabaseConnectionException(
                    "Could not begin database transaction",
                    e
            );
        }
    }


    public void commitTransaction() {
        Connection connection = requireTransactionConnection();

        try {
            connection.commit();
        } catch (SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not commit database transaction",
                    e
            );
        } finally {
            closeTransactionConnection();
        }
    }


    public void rollbackTransaction() {
        Connection connection = requireTransactionConnection();

        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not roll back database transaction",
                    e
            );
        } finally {
            closeTransactionConnection();
        }
    }

    public boolean isTransactionActive() {
        return transactionConnection.get() != null;
    }

    public void initialize() {
        try {
            Files.createDirectories(databasePath.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new DatabaseConnectionException(
                    "Could not create database directory",
                    e
            );
        }

        try (Connection connection = createConnection();
             InputStream input =
                     DatabaseManager.class.getResourceAsStream("/schema.sql")) {

            if (input == null) {
                throw new DatabaseConnectionException(
                        "schema.sql not found"
                );
            }

            String schema =
                    new String(
                            input.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(schema);
                migrate(connection);
            }

        } catch (IOException | SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not initialize database",
                    e
            );
        }
    }

    private Connection createConnection() {
        try {
            String databaseUrl =
                    "jdbc:sqlite:" + databasePath;

            Connection connection =
                    DriverManager.getConnection(databaseUrl);

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            return connection;

        } catch (SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not connect to database",
                    e
            );
        }
    }

    private Connection requireTransactionConnection() {
        Connection connection = transactionConnection.get();

        if (connection == null) {
            throw new IllegalStateException(
                    "No active database transaction"
            );
        }

        return connection;
    }

    private void closeTransactionConnection() {
        Connection connection = transactionConnection.get();

        transactionConnection.remove();

        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not close transaction connection",
                    e
            );
        }
    }

    private static final int SCHEMA_VERSION = 1;


    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
                version = rs.getInt(1);
            }
            if (version >= SCHEMA_VERSION) {
                return;
            }
            if (usersTableIsCaseSensitive(statement)) {
                rebuildUsersTable(connection, statement);
            } else {
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        }
    }

    private boolean usersTableIsCaseSensitive(Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'")) {
            return !rs.getString(1).toUpperCase().contains("COLLATE NOCASE");
        }
    }

    private void rebuildUsersTable(Connection connection, Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "SELECT lower(username), group_concat(id) FROM users "
                        + "GROUP BY lower(username) HAVING count(*) > 1")) {
            if (rs.next()) {
                throw new SQLException("Cannot migrate: usernames differing only by case: "
                        + rs.getString(1) + " (ids " + rs.getString(2) + "). Merge them first.");
            }
        }

        statement.execute("PRAGMA foreign_keys = OFF");   // must be outside a transaction
        connection.setAutoCommit(false);
        try {
            statement.execute("""
                CREATE TABLE users_new (
                  id INTEGER PRIMARY KEY,
                  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')))""");
            statement.execute("INSERT INTO users_new (id, username, created_at) "
                    + "SELECT id, username, created_at FROM users");
            statement.execute("DROP TABLE users");
            statement.execute("ALTER TABLE users_new RENAME TO users");
            try (ResultSet rs = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (rs.next()) {
                    throw new SQLException("Migration left dangling foreign keys");
                }
            }
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }
}