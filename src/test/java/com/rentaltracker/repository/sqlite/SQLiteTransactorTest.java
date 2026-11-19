package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.User;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteTransactorTest {

    @TempDir
    Path tempDir;

    @Test
    void commitsWhenWorkSucceeds() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        ItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        SQLiteTransactor transactor =
                new SQLiteTransactor(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice", "test-hash")
        );

        Item item = Item.newListing(
                owner.id(),
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        Item saved = transactor.inTransaction(
                () -> itemRepository.insert(item)
        );

        assertTrue(
                itemRepository.findById(saved.id()).isPresent()
        );
    }

    private DatabaseManager createDatabase() {
        Path database =
                tempDir.resolve("rental-tracker.db");

        DatabaseManager databaseManager =
                new DatabaseManager(database);

        databaseManager.initialize();

        return databaseManager;
    }

    @Test
    void rollsBackWhenWorkThrowsRuntimeException() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        ItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        SQLiteTransactor transactor =
                new SQLiteTransactor(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice", "test-hash")
        );

        Item item = Item.newListing(
                owner.id(),
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        assertThrows(
                RuntimeException.class,
                () -> transactor.inTransaction(() -> {
                    itemRepository.insert(item);

                    throw new RuntimeException("Something went wrong");
                })
        );

        assertTrue(
                itemRepository.findById(1L).isEmpty()
        );
    }

    @Test
    void nestedTransactionJoinsOuterTransaction() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        ItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        SQLiteTransactor transactor =
                new SQLiteTransactor(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice", "test-hash")
        );

        Item firstItem = Item.newListing(
                owner.id(),
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        Item secondItem = Item.newListing(
                owner.id(),
                "Camera",
                "A digital camera",
                BigDecimal.valueOf(50)
        );

        transactor.inTransaction(() -> {
            itemRepository.insert(firstItem);

            transactor.inTransaction(() -> {
                itemRepository.insert(secondItem);
            });
        });

        assertEquals(
                2,
                itemRepository.findByOwnerId(owner.id()).size()
        );
    }

    @Test
    void nestedFailureRollsBackOuterTransaction() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        ItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        SQLiteTransactor transactor =
                new SQLiteTransactor(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice", "test-hash")
        );

        Item firstItem = Item.newListing(
                owner.id(),
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        Item secondItem = Item.newListing(
                owner.id(),
                "Camera",
                "A digital camera",
                BigDecimal.valueOf(50)
        );

        assertThrows(
                RuntimeException.class,
                () -> transactor.inTransaction(() -> {

                    itemRepository.insert(firstItem);

                    transactor.inTransaction(() -> {
                        itemRepository.insert(secondItem);

                        throw new RuntimeException("Something went wrong");
                    });
                })
        );

        assertTrue(
                itemRepository.findByOwnerId(owner.id()).isEmpty()
        );
    }
}