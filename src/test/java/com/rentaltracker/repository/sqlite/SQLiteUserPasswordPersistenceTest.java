package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.NotNullConstraintException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteUserPasswordPersistenceTest {

    private static final String BCRYPT_HASH = "$2b$12$abcdefghijklmnopqrstuu123456789012345678901234567890";

    @TempDir
    Path tempDir;

    @Test
    void insertAndLookupRoundTripThePasswordHashWithoutPlaintext() throws Exception {
        DatabaseManager manager = createDatabase();
        SQLiteUserRepository repository = new SQLiteUserRepository(manager);

        User saved = repository.insert(User.newUser("alice", BCRYPT_HASH));
        User loaded = repository.findByUsername("ALICE").orElseThrow();

        assertNotNull(saved.id());
        assertEquals(BCRYPT_HASH, saved.password());
        assertEquals(BCRYPT_HASH, loaded.password());

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT password FROM users WHERE id = " + saved.id())) {
            assertTrue(rows.next());
            assertEquals(BCRYPT_HASH, rows.getString(1));
        }
    }

    @Test
    void insertRejectsAUserWithoutAPasswordHash() {
        SQLiteUserRepository repository = new SQLiteUserRepository(createDatabase());

        assertThrows(
                NotNullConstraintException.class,
                () -> repository.insert(User.newUser("missing-password"))
        );
    }

    private DatabaseManager createDatabase() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("users.db"));
        manager.initialize();
        return manager;
    }
}
