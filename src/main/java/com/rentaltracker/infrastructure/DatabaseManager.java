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

    public static final int SCHEMA_VERSION = 3;

    private final Path databasePath;
    private final ConnectionFactory connectionFactory;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public DatabaseManager(Path databasePath) {
        this(databasePath, sqliteFactory(databasePath));
    }

    DatabaseManager(Path databasePath, ConnectionFactory connectionFactory) {
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path must not be null");
        }
        if (connectionFactory == null) {
            throw new IllegalArgumentException("Connection factory must not be null");
        }
        this.databasePath = databasePath;
        this.connectionFactory = connectionFactory;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private static ConnectionFactory sqliteFactory(Path databasePath) {
        return () -> DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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
            throw new DatabaseConnectionException("Could not close database connection", e);
        }
    }

    public void beginTransaction() {
        if (transactionConnection.get() != null) {
            throw new IllegalStateException("A transaction is already active");
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
            throw new DatabaseConnectionException("Could not begin database transaction", e);
        }
    }

    public void commitTransaction() {
        Connection connection = requireTransactionConnection();
        RuntimeException failure = null;
        try {
            connection.commit();
        } catch (SQLException e) {
            failure = new DatabaseConnectionException("Could not commit database transaction", e);
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
            failure = new DatabaseConnectionException("Could not roll back database transaction", e);
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
            throw new DatabaseConnectionException("Could not create database directory", e);
        }

        try (Connection connection = createConnection();
             InputStream input = DatabaseManager.class.getResourceAsStream("/schema.sql")) {

            String schema = readSchema(input);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(schema);
            }
            migrate(connection, schema);
        } catch (IOException | SQLException e) {
            throw new DatabaseConnectionException("Could not initialize database", e);
        }
    }

    static String readSchema(InputStream input) throws IOException {
        if (input == null) {
            throw new DatabaseConnectionException("schema.sql not found");
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

      //Applies the checked-in development/demo seed after schema initialization.

    public void initializeDevelopmentData() {
        try (Connection connection = createConnection();
             InputStream input = DatabaseManager.class.getResourceAsStream("/data.sql")) {
            String seed = readSchema(input);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(seed);
            }
        } catch (IOException | SQLException e) {
            throw new DatabaseConnectionException("Could not initialize development data", e);
        }
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
            throw new DatabaseConnectionException("Could not connect to database", e);
        }
    }

    private Connection requireTransactionConnection() {
        Connection connection = transactionConnection.get();
        if (connection == null) {
            throw new IllegalStateException("No active database transaction");
        }
        return connection;
    }

    private void closeTransactionConnection() {
        Connection connection = transactionConnection.get();
        transactionConnection.remove();
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Could not close transaction connection", e);
        }
    }

    private void migrate(Connection connection, String schema) throws SQLException {
        int version;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            version = resultSet.getInt(1);
        }
        if (version < SCHEMA_VERSION) {
            upgrade(connection, schema);
        }
    }

    private void upgrade(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (legacyUsersNeedReset(statement)) {
                resetLegacyDatabase(connection, statement, schema);
            } else {
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        }
    }

    private boolean legacyUsersNeedReset(Statement statement) throws SQLException {
        if (!usersTableHasPassword(statement)) {
            return true;
        }
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT 1 FROM users WHERE password IS NULL OR trim(password) = '' LIMIT 1")) {
            return resultSet.next();
        }
    }

    static boolean usersTableIsCaseSensitive(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'")) {
            if (!resultSet.next()) {
                throw new SQLException("Cannot inspect users table: table does not exist");
            }
            return isCaseSensitive(resultSet.getString(1));
        }
    }

    static boolean isCaseSensitive(String tableDefinition) {
        return tableDefinition == null
                || !tableDefinition.toUpperCase().contains("COLLATE NOCASE");
    }

    static boolean usersTableHasPassword(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(users)")) {
            while (resultSet.next()) {
                if ("password".equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void resetLegacyDatabase(Connection connection, Statement statement, String schema)
            throws SQLException {
        Throwable failure = null;
        boolean transactionStarted = false;
        boolean rollbackFailed = false;

        try {
            // Must run outside a transaction
            statement.execute("PRAGMA foreign_keys = OFF");
            connection.setAutoCommit(false);
            transactionStarted = true;

            // Legacy accounts cannot be safely associated with real credentials. Drop all
            // application data together and recreate the schema instead of leaving unusable users.
            statement.execute("DROP TABLE IF EXISTS rentals");
            statement.execute("DROP TABLE IF EXISTS listed_items");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.executeUpdate(schema);

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

        // Cleanup – only attempt if rollback did not fail
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

            // Re-enable foreign keys only after auto-commit is restored
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

    private static Throwable recordFailure(Throwable originalFailure, SQLException cleanupFailure) {
        if (originalFailure == null) {
            return cleanupFailure;
        }
        originalFailure.addSuppressed(cleanupFailure);
        return originalFailure;
    }
}
