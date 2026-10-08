package com.rentaltracker.service;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.sqlite.SQLiteUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    @TempDir
    Path tempDir;

    private UserService service;

    @BeforeEach
    void setUp() {
        DatabaseManager db = createDatabase();
        service = new UserService(new SQLiteUserRepository(db));
    }

    @Test
    void findOwnerIsEmptyBeforeRegistration() {
        assertTrue(service.findOwner().isEmpty());
    }

    @Test
    void registerOwnerSavesTheFirstUserAsOwner() {
        User owner = service.registerOwner("liisa");
        assertEquals(Optional.of(owner), service.findOwner());
    }

    @Test
    void registerOwnerRejectsBlankUsername() {
        assertThrows(ValidationException.class, () -> service.registerOwner("   "));
        assertThrows(ValidationException.class, () -> service.registerOwner(null));
    }

    @Test
    void findOrCreateRenterReusesAnExistingAccount() {
        User first = service.findOrCreateRenter("meelis");
        User second = service.findOrCreateRenter("meelis");
        assertEquals(first.id(), second.id());
    }

    @Test
    void findOrCreateRenterCreatesWhenAbsent() {
        User renter = service.findOrCreateRenter("uku");
        assertEquals("uku", renter.username());
    }

    @Test
    void findOrCreateRenterRejectsBlankName() {
        assertThrows(ValidationException.class, () -> service.findOrCreateRenter(" "));
    }

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");
        DatabaseManager databaseManager = new DatabaseManager(database);
        databaseManager.initialize();
        return databaseManager;
    }
}
