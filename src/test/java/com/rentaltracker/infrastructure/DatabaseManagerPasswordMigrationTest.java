package com.rentaltracker.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerPasswordMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void resetsLegacyUsersTableInsteadOfRetainingPasswordlessAccounts() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users ("
                    + "id INTEGER PRIMARY KEY, "
                    + "username TEXT NOT NULL UNIQUE, "
                    + "created_at TEXT NOT NULL)");
            statement.execute("INSERT INTO users (id, username, created_at) "
                    + "VALUES (7, 'alice', '2026-10-17T00:00:00.000Z')");
            statement.execute("PRAGMA user_version = 1");
        }

        DatabaseManager manager = new DatabaseManager(database);
        manager.initialize();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(0, scalar(statement, "SELECT count(*) FROM users"));
            assertEquals(0, scalar(statement, "SELECT count(*) FROM listed_items"));
            assertEquals(0, scalar(statement, "SELECT count(*) FROM rentals"));
            assertEquals(DatabaseManager.SCHEMA_VERSION,
                    scalar(statement, "PRAGMA user_version"));
            assertTrue(hasColumn(statement, "password"));
        }
    }

    @Test
    void resetsAnIntermediateDatabaseContainingEmptyPasswords() throws Exception {
        Path database = tempDir.resolve("intermediate.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users ("
                    + "id INTEGER PRIMARY KEY, "
                    + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "password TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL)");
            statement.execute("INSERT INTO users (id, username, password, created_at) "
                    + "VALUES (7, 'alice', '', '2026-10-17T00:00:00.000Z')");
            statement.execute("PRAGMA user_version = 2");
        }

        DatabaseManager manager = new DatabaseManager(database);
        manager.initialize();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(0, scalar(statement, "SELECT count(*) FROM users"));
            assertEquals(DatabaseManager.SCHEMA_VERSION,
                    scalar(statement, "PRAGMA user_version"));
        }
    }

    private static long scalar(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static boolean hasColumn(Statement statement, String expected) throws Exception {
        try (ResultSet rows = statement.executeQuery("PRAGMA table_info(users)")) {
            while (rows.next()) {
                if (expected.equalsIgnoreCase(rows.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
