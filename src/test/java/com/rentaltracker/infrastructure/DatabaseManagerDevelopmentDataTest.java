package com.rentaltracker.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerDevelopmentDataTest {
    @TempDir
    Path tempDir;

    @Test
    void developmentSeedCreatesTwoUsersAndSixItems() throws Exception {
        DatabaseManager manager = initializeAndSeed();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT count(*) FROM users"));
            assertEquals(6, scalar(statement, "SELECT count(*) FROM listed_items"));
            assertEquals(3, scalar(statement,
                    "SELECT count(*) FROM listed_items WHERE owner_id = 1001"));
            assertEquals(3, scalar(statement,
                    "SELECT count(*) FROM listed_items WHERE owner_id = 1002"));
            assertEquals(0, scalar(statement,
                    "SELECT count(*) FROM listed_items WHERE status <> 'available'"));
            assertTrue(scalar(statement,
                    "SELECT count(*) FROM users WHERE password LIKE '$2b$%'" ) == 2);
        }
    }

    @Test
    void developmentSeedIsIdempotentAndDoesNotOverwriteExistingRows() throws Exception {
        DatabaseManager manager = initializeAndSeed();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET username = 'custom-alice' WHERE id = 1001");
            statement.executeUpdate("UPDATE listed_items SET name = 'Custom item' WHERE id = 1101");
        }

        manager.initializeDevelopmentData();

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT count(*) FROM users"));
            assertEquals(6, scalar(statement, "SELECT count(*) FROM listed_items"));
            assertEquals(1, scalar(statement,
                    "SELECT count(*) FROM users WHERE username = 'custom-alice'"));
            assertEquals(1, scalar(statement,
                    "SELECT count(*) FROM listed_items WHERE name = 'Custom item'"));
        }
    }

    private DatabaseManager initializeAndSeed() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("seed.db"));
        manager.initialize();
        manager.initializeDevelopmentData();
        return manager;
    }

    private static long scalar(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }
}
