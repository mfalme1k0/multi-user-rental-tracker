package com.rentaltracker.infrastructure;

import com.rentaltracker.exception.DatabaseConnectionException;

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

public final class DatabaseManager {

    public static final int SCHEMA_VERSION = 1;

    private final Path databasePath;
    private final ConnectionFactory connectionFactory;

    private final ThreadLocal<Connection> transactionConnection =
            new ThreadLocal<>();

    public DatabaseManager(Path databasePath) {
        this(databasePath, sqliteFactory(databasePath));
    }

    DatabaseManager(Path databasePath, ConnectionFactory connectionFactory) {
        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path must not be null"
            );
        }

        if (connectionFactory == null) {
            throw new IllegalArgumentException(
                    "Connection factory must not be null"
            );
        }

        this.databasePath = databasePath;
        this.connectionFactory = connectionFactory;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private static ConnectionFactory sqliteFactory(Path databasePath) {
        return () -> DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath
        );
    }

    //return active connection, if none exists create one
    public Connection getConnection() {
        Connection activeConnection = transactionConnection.get();

        if (activeConnection != null) {
            return activeConnection;
        }

        return createConnection();
    }

    //release normal repository connection
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

    //commit transaction and release connection
    public void commitTransaction() {
        Connection connection = requireTransactionConnection();
        RuntimeException failure = null;

        try {
            connection.commit();
        } catch (SQLException e) {
            failure = new DatabaseConnectionException(
                    "Could not commit database transaction",
                    e
            );
        }

        try {
            closeTransactionConnection();
        } catch (RuntimeException closeException) {
            if (failure != null) {
                failure.addSuppressed(closeException);
            } else {
                failure = closeException;
            }
        }

        if (failure != null) {
            throw failure;
        }
    }


    public void rollbackTransaction() {
        Connection connection = requireTransactionConnection();
        RuntimeException failure = null;

        try {
            connection.rollback();
        } catch (SQLException e) {
            failure = new DatabaseConnectionException(
                    "Could not roll back database transaction",
                    e
            );
        }

        try {
            closeTransactionConnection();
        } catch (RuntimeException closeException) {
            if (failure != null) {
                failure.addSuppressed(closeException);
            } else {
                failure = closeException;
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    public boolean isTransactionActive() {
        return transactionConnection.get() != null;
    }

    public void initialize() {
        try {
            Path parent = databasePath.toAbsolutePath().getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new DatabaseConnectionException(
                    "Could not create database directory",
                    e
            );
        }

        try (Connection connection = createConnection();
             InputStream input =
                     DatabaseManager.class.getResourceAsStream("/schema.sql")) {

            String schema = readSchema(input);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(schema);
            }

            migrate(connection);

        } catch (IOException | SQLException e) {
            throw new DatabaseConnectionException(
                    "Could not initialize database",
                    e
            );
        }
    }


    static String readSchema(InputStream input) throws IOException {
        if (input == null) {
            throw new DatabaseConnectionException(
                    "schema.sql not found"
            );
        }

        return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    private Connection createConnection() {
        Connection connection = null;

        try {
            connection = connectionFactory.open();

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            return connection;

        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    e.addSuppressed(closeException);
                }
            }

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


    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery("PRAGMA user_version")) {

            int version = resultSet.getInt(1);

            if (version >= SCHEMA_VERSION) {
                return;
            }
        }

        upgrade(connection);
    }


    private void upgrade(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (usersTableIsCaseSensitive(statement)) {
                rebuildUsersTable(connection, statement);
            } else {
                statement.execute(
                        "PRAGMA user_version = " + SCHEMA_VERSION
                );
            }
        }
    }

    private boolean usersTableIsCaseSensitive(Statement statement)
            throws SQLException {

        try (ResultSet resultSet = statement.executeQuery(
                "SELECT sql FROM sqlite_master "
                        + "WHERE type = 'table' AND name = 'users'")) {

            if (!resultSet.next()) {
                throw new SQLException(
                        "Cannot inspect users table: table does not exist"
                );
            }

            String tableDefinition = resultSet.getString(1);

            return tableDefinition == null
                    || !tableDefinition.toUpperCase()
                    .contains("COLLATE NOCASE");
        }
    }

    private void rebuildUsersTable(
            Connection connection,
            Statement statement
    ) throws SQLException {

        try (ResultSet rs = statement.executeQuery(
                "SELECT lower(username), group_concat(id) FROM users "
                        + "GROUP BY lower(username) HAVING count(*) > 1")) {

            if (rs.next()) {
                throw new SQLException(
                        "Cannot migrate: usernames differing only by case: "
                                + rs.getString(1)
                                + " (ids "
                                + rs.getString(2)
                                + "). Merge them first."
                );
            }
        }



        Throwable failure = null;
        boolean transactionStarted = false;
        boolean rollbackFailed = false;

        try {
            connection.setAutoCommit(false);
            transactionStarted = true;

            statement.execute("""
            CREATE TABLE users_new (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at TEXT NOT NULL DEFAULT (
                    strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                )
            )
            """);

            statement.execute("""
            INSERT INTO users_new (id, username, created_at)
            SELECT id, username, created_at FROM users
            """);

            statement.execute("DROP TABLE users");
            statement.execute("ALTER TABLE users_new RENAME TO users");

            try (ResultSet rs =
                         statement.executeQuery("PRAGMA foreign_key_check")) {

                if (rs.next()) {
                    throw new SQLException(
                            "Migration left dangling foreign keys"
                    );
                }
            }

            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);

            connection.commit();
            transactionStarted = false;

        } catch (SQLException | RuntimeException migrationFailure) {
            failure = migrationFailure;

            if (transactionStarted) {
                try {
                    connection.rollback();
                    transactionStarted = false;
                } catch (SQLException rollbackFailure) {
                    migrationFailure.addSuppressed(rollbackFailure);
                    rollbackFailed = true;
                }
            }
        }


        if (!rollbackFailed) {
            boolean autoCommitRestored = false;

            try {
                if (!connection.getAutoCommit()) {
                    connection.setAutoCommit(true);
                }

                autoCommitRestored = true;

            } catch (SQLException cleanupFailure) {
                failure = recordFailure(failure, cleanupFailure);
            }

            // Re-enable foreign keys only after auto-commit is restored.
            if (autoCommitRestored) {
                try {
                    statement.execute("PRAGMA foreign_keys = ON");
                } catch (SQLException cleanupFailure) {
                    failure = recordFailure(failure, cleanupFailure);
                }
            }
        }

        if (failure instanceof SQLException sqlFailure) {
            throw sqlFailure;
        }

        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
    }

    private static Throwable recordFailure(
            Throwable originalFailure,
            SQLException cleanupFailure
    ) {
        if (originalFailure == null) {
            return cleanupFailure;
        }

        originalFailure.addSuppressed(cleanupFailure);
        return originalFailure;
    }
}

