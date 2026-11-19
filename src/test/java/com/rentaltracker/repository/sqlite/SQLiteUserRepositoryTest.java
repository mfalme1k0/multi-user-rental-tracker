package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.exception.NotNullConstraintException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteUserRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void insertReturnsUserWithGeneratedIdAndCreatedAt() {
        SQLiteUserRepository repository = createRepository();

        User user = User.newUser("alice", "test-hash");

        User saved = repository.insert(user);

        assertNotNull(saved.id());
        assertEquals("alice", saved.username());
        assertEquals("test-hash", saved.password());
        assertNotNull(saved.createdAt());
    }

    @Test
    void insertRejectsDuplicateUsername() {
        SQLiteUserRepository repository = createRepository();

        repository.insert(User.newUser("alice", "test-hash"));

        assertThrows(
                UniqueConstraintException.class,
                () -> repository.insert(User.newUser("alice", "test-hash"))
        );
    }

    @Test
    void insertRejectsNullUsername() {
        SQLiteUserRepository repository = createRepository();

        User user = new User(
                null,
                null,
                null,
                null
        );

        assertThrows(
                NotNullConstraintException.class,
                () -> repository.insert(user)
        );
    }

    private SQLiteUserRepository createRepository() {
        Path database = tempDir.resolve("rental-tracker.db");

        DatabaseManager databaseManager =
                new DatabaseManager(database);

        databaseManager.initialize();

        return new SQLiteUserRepository(databaseManager);
    }

    @Test
    void findByIdReturnsUserWhenUserExists() {
        SQLiteUserRepository repository = createRepository();

        User saved = repository.insert(User.newUser("alice", "test-hash"));

        var result = repository.findById(saved.id());

        assertTrue(result.isPresent());
        assertEquals(saved, result.get());
    }

    @Test
    void findByIdReturnsEmptyWhenUserDoesNotExist() {
        SQLiteUserRepository repository = createRepository();

        var result = repository.findById(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByUsernameReturnsUserWhenUserExists() {
        SQLiteUserRepository repository = createRepository();

        User saved = repository.insert(User.newUser("alice", "test-hash"));

        var result = repository.findByUsername("alice");

        assertTrue(result.isPresent());
        assertEquals(saved, result.get());
    }

    @Test
    void findByUsernameReturnsEmptyWhenUsernameDoesNotExist() {
        SQLiteUserRepository repository = createRepository();

        var result = repository.findByUsername("does-not-exist");

        assertTrue(result.isEmpty());
    }

    @Test
    void findFirstReturnsLowestIdUser() {
        SQLiteUserRepository repository = createRepository();

        User alice = repository.insert(User.newUser("alice", "test-hash"));
        repository.insert(User.newUser("bob", "test-hash"));

        var result = repository.findFirst();

        assertTrue(result.isPresent());
        assertEquals(alice, result.get());
    }

    @Test
    void findFirstReturnsEmptyWhenThereAreNoUsers() {
        SQLiteUserRepository repository = createRepository();

        var result = repository.findFirst();

        assertTrue(result.isEmpty());
    }

    @Test
    void getByIdReturnsUserWhenUserExists() {
        SQLiteUserRepository repository = createRepository();

        User saved = repository.insert(User.newUser("alice", "test-hash"));

        User result = repository.getById(saved.id());

        assertEquals(saved, result);
    }

    @Test
    void getByIdThrowsWhenUserDoesNotExist() {
        SQLiteUserRepository repository = createRepository();

        assertThrows(
                NotFoundException.class,
                () -> repository.getById(999L)
        );
    }

    @Test
    void insertRejectsUsernameDifferingOnlyByCase() {
        SQLiteUserRepository repository = createRepository();

        repository.insert(User.newUser("bob", "test-hash"));

        assertThrows(UniqueConstraintException.class,
                () -> repository.insert(User.newUser("Bob", "test-hash")));
        assertThrows(UniqueConstraintException.class,
                () -> repository.insert(User.newUser("BOB", "test-hash")));
    }

    @Test
    void findByUsernameIgnoresCase() {
        SQLiteUserRepository repository = createRepository();

        User saved = repository.insert(User.newUser("mfalme", "test-hash"));

        var result = repository.findByUsername("Mfalme");

        assertTrue(result.isPresent());
        assertEquals(saved.id(), result.get().id());
    }
}
