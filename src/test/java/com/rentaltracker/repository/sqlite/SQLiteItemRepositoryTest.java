package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.ForeignKeyConstraintException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.exception.NotNullConstraintException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteItemRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void insertReturnsItemWithGeneratedIdAndCreatedAt() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item item = Item.newListing(
                owner.id(),
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        Item saved = itemRepository.insert(item);

        assertNotNull(saved.id());
        assertEquals(owner.id(), saved.ownerId());
        assertEquals("Mountain Bike", saved.name());
        assertEquals("A red mountain bike", saved.description());
        assertEquals(
                BigDecimal.valueOf(100),
                saved.costPerDay()
        );
        assertEquals(ItemStatus.AVAILABLE, saved.status());
        assertNotNull(saved.createdAt());
    }

    @Test
    void insertRejectsUnknownOwner() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteItemRepository repository =
                new SQLiteItemRepository(databaseManager);

        Item item = Item.newListing(
                999L,
                "Mountain Bike",
                "A red mountain bike",
                BigDecimal.valueOf(100)
        );

        assertThrows(
                ForeignKeyConstraintException.class,
                () -> repository.insert(item)
        );
    }

    @Test
    void insertRejectsNullName() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item item = new Item(
                null,
                owner.id(),
                null,
                "Description",
                BigDecimal.valueOf(100),
                ItemStatus.AVAILABLE,
                null
        );

        assertThrows(
                NotNullConstraintException.class,
                () -> itemRepository.insert(item)
        );
    }

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");

        DatabaseManager databaseManager =
                new DatabaseManager(database);

        databaseManager.initialize();

        return databaseManager;
    }
    @Test
    void findByIdReturnsSavedItem() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item saved = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Mountain Bike",
                        "A red mountain bike",
                        BigDecimal.valueOf(100)
                )
        );

        Optional<Item> found = itemRepository.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void getByIdReturnsSavedItem() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item saved = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Mountain Bike",
                        "A red mountain bike",
                        BigDecimal.valueOf(100)
                )
        );

        Item found = itemRepository.getById(saved.id());

        assertEquals(saved, found);
    }

    @Test
    void findByOwnerIdReturnsAllOwnerItems() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User alice = userRepository.insert(
                User.newUser("alice")
        );

        Item first = itemRepository.insert(
                Item.newListing(
                        alice.id(),
                        "Mountain Bike",
                        "Bike",
                        BigDecimal.valueOf(100)
                )
        );

        Item second = itemRepository.insert(
                Item.newListing(
                        alice.id(),
                        "Camera",
                        "Camera",
                        BigDecimal.valueOf(50)
                )
        );

        List<Item> items = itemRepository.findByOwnerId(alice.id());

        assertEquals(List.of(first, second), items);
    }

    @Test
    void findByOwnerIdDoesNotReturnAnotherOwnersItems() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User alice = userRepository.insert(
                User.newUser("alice")
        );

        User bob = userRepository.insert(
                User.newUser("bob")
        );

        Item alicesItem = itemRepository.insert(
                Item.newListing(
                        alice.id(),
                        "Mountain Bike",
                        "Bike",
                        BigDecimal.valueOf(100)
                )
        );

        itemRepository.insert(
                Item.newListing(
                        bob.id(),
                        "Camera",
                        "Camera",
                        BigDecimal.valueOf(50)
                )
        );

        List<Item> items = itemRepository.findByOwnerId(alice.id());

        assertEquals(List.of(alicesItem), items);
    }

    @Test
    void findByOwnerIdReturnsEmptyListWhenOwnerHasNoItems() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        List<Item> items = itemRepository.findByOwnerId(owner.id());

        assertTrue(items.isEmpty());
    }
    @Test
    void findByOwnerIdAndStatusReturnsMatchingItems() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item first = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Mountain Bike",
                        "Bike",
                        BigDecimal.valueOf(100)
                )
        );

        Item second = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Camera",
                        BigDecimal.valueOf(50)
                )
        );

        List<Item> items =
                itemRepository.findByOwnerIdAndStatus(
                        owner.id(),
                        ItemStatus.AVAILABLE
                );

        assertEquals(List.of(first, second), items);
    }

    @Test
    void findByOwnerIdAndStatusReturnsEmptyWhenNothingMatches() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        List<Item> items =
                itemRepository.findByOwnerIdAndStatus(
                        owner.id(),
                        ItemStatus.RENTED
                );

        assertTrue(items.isEmpty());
    }

    @Test
    void updateStatusChangesItemStatus() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteUserRepository userRepository =
                new SQLiteUserRepository(databaseManager);

        SQLiteItemRepository itemRepository =
                new SQLiteItemRepository(databaseManager);

        User owner = userRepository.insert(
                User.newUser("alice")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Mountain Bike",
                        "Bike",
                        BigDecimal.valueOf(100)
                )
        );

        itemRepository.updateStatus(
                item.id(),
                ItemStatus.RENTED
        );

        Item updated = itemRepository.getById(item.id());

        assertEquals(ItemStatus.RENTED, updated.status());
    }

    @Test
    void updateStatusThrowsWhenItemDoesNotExist() {
        DatabaseManager databaseManager = createDatabase();

        SQLiteItemRepository repository =
                new SQLiteItemRepository(databaseManager);

        assertThrows(
                NotFoundException.class,
                () -> repository.updateStatus(
                        999L,
                        ItemStatus.RENTED
                )
        );
    }

    @Test
    void getByIdThrowsWhenItemDoesNotExist() {
        SQLiteItemRepository repository =
                new SQLiteItemRepository(createDatabase());

        assertThrows(
                NotFoundException.class,
                () -> repository.getById(999L)
        );
    }

    @Test
    void findByIdReturnsEmptyWhenItemDoesNotExist() {
        SQLiteItemRepository repository =
                new SQLiteItemRepository(createDatabase());

        assertTrue(repository.findById(999L).isEmpty());
    }
}