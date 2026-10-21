package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteItemMarketplaceTest {

    @TempDir
    Path tempDir;

    @Test
    void browseExcludesCurrentUsersItemsAndNonAvailableItems() {
        DatabaseManager manager = createDatabase();
        SQLiteUserRepository users = new SQLiteUserRepository(manager);
        SQLiteItemRepository items = new SQLiteItemRepository(manager);
        User current = users.insert(User.newUser("alice", "hash-a"));
        User other = users.insert(User.newUser("bob", "hash-b"));

        Item own = insert(items, current, "own");
        Item first = insert(items, other, "first");
        Item rented = insert(items, other, "rented");
        items.updateStatus(rented.id(), ItemStatus.RENTED);

        Page<Item> page = items.findAvailableExcludingOwner(current.id(), 0, 5);

        assertEquals(List.of(first), page.items());
        assertFalse(page.hasNext());
        assertFalse(page.items().contains(own));
        assertFalse(page.items().contains(rented));
    }

    @Test
    void browseUsesPageSizeAndLookAheadRow() {
        DatabaseManager manager = createDatabase();
        SQLiteUserRepository users = new SQLiteUserRepository(manager);
        SQLiteItemRepository items = new SQLiteItemRepository(manager);
        User current = users.insert(User.newUser("alice", "hash-a"));
        User other = users.insert(User.newUser("bob", "hash-b"));
        Item first = insert(items, other, "first");
        Item second = insert(items, other, "second");
        Item third = insert(items, other, "third");

        Page<Item> firstPage = items.findAvailableExcludingOwner(current.id(), 0, 2);
        Page<Item> secondPage = items.findAvailableExcludingOwner(current.id(), 1, 2);

        assertEquals(List.of(first, second), firstPage.items());
        assertTrue(firstPage.hasNext());
        assertEquals(List.of(third), secondPage.items());
        assertFalse(secondPage.hasNext());
    }

    @Test
    void markRentedIfAvailableClaimsOnlyOnce() {
        DatabaseManager manager = createDatabase();
        SQLiteUserRepository users = new SQLiteUserRepository(manager);
        SQLiteItemRepository items = new SQLiteItemRepository(manager);
        User owner = users.insert(User.newUser("owner", "hash"));
        Item item = insert(items, owner, "item");

        assertTrue(items.markRentedIfAvailable(item.id()));
        assertFalse(items.markRentedIfAvailable(item.id()));
        assertEquals(ItemStatus.RENTED, items.getById(item.id()).status());
        assertFalse(items.markRentedIfAvailable(999L));
    }

    @Test
    void browseRejectsInvalidPageArguments() {
        SQLiteItemRepository items = new SQLiteItemRepository(createDatabase());

        assertThrows(IllegalArgumentException.class,
                () -> items.findAvailableExcludingOwner(1L, -1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> items.findAvailableExcludingOwner(1L, 0, 0));
    }

    private DatabaseManager createDatabase() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("marketplace.db"));
        manager.initialize();
        return manager;
    }

    private Item insert(SQLiteItemRepository items, User owner, String name) {
        return items.insert(Item.newListing(owner.id(), name, "description", BigDecimal.ONE));
    }
}
