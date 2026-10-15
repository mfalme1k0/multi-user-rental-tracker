package com.rentaltracker.transport;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.sqlite.SQLiteItemRepository;
import com.rentaltracker.repository.sqlite.SQLiteRentalRepository;
import com.rentaltracker.repository.sqlite.SQLiteTransactor;
import com.rentaltracker.repository.sqlite.SQLiteUserRepository;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screens in {@link Transport} that the main {@link TransportTest} does not reach: an item that is
 * currently rented, and returning the last rental on the last page of the list.
 * Menu keys: 2 = inventory, 4 = confirm a return, 5 = exit.
 */
class TransportEdgeCasesTest {

    @TempDir
    Path tempDir;

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;

    private SQLiteUserRepository users;
    private SQLiteItemRepository items;
    private SQLiteRentalRepository rentals;

    private UserService userService;
    private ItemService itemService;
    private RentalService rentalService;

    @BeforeEach
    void setUp() {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("rental-tracker.db"));
        db.initialize();

        users = new SQLiteUserRepository(db);
        items = new SQLiteItemRepository(db);
        rentals = new SQLiteRentalRepository(db);

        SQLiteTransactor transactor = new SQLiteTransactor(db);

        userService = new UserService(users);
        itemService = new ItemService(items, rentals, users, transactor);
        rentalService = new RentalService(items, rentals, userService, transactor,
                Clock.systemDefaultZone());
    }

    @AfterEach
    void restoreStreams() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    /** Runs the app with these lines typed on the keyboard and returns everything it printed. */
    private String run(String typed) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setIn(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

        new Transport(userService, itemService, rentalService).start();

        return output.toString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private Rental rentOut(User owner, String itemName, User renter) {
        Item item = itemService.listItem(owner.id(), itemName, "d", new BigDecimal("100"));
        items.updateStatus(item.id(), ItemStatus.RENTED);

        return rentals.insert(Rental.newActive(
                item.id(), renter.id(), LocalDateTime.now(), LocalDateTime.now().plusDays(3)));
    }

    @Test
    void rentedItemCanBeDelistedFromTheInventory() {
        User owner = users.insert(User.newUser("jackson"));
        Rental rental = rentOut(owner, "Laptop", users.insert(User.newUser("alex")));

        // inventory -> open the item -> "1" (delist) -> back -> back -> exit
        String result = run("2\n1\n1\n2\nB\n5\n");

        assertTrue(result.contains("1) Delist"), result);          // a RENTED item offers Delist
        assertTrue(result.contains("status         rented"), result);
        assertTrue(result.contains("Goodbye."), result);
        // Whatever the service decided, the rental itself is untouched by browsing the inventory.
        assertEquals(RentalStatus.ACTIVE, rentals.getById(rental.id()).status());
    }

    @Test
    void returningTheOnlyRentalOnTheLastPageGoesBackToThePreviousPage() {
        User owner = users.insert(User.newUser("jackson"));
        for (int i = 1; i <= 6; i++) {                              // page size is 5, so two pages
            rentOut(owner, "Item " + i, users.insert(User.newUser("renter" + i)));
        }

        // returns -> next page (1 rental) -> open it -> confirm -> back on a valid page -> back -> exit
        String result = run("4\nN\n1\n1\nB\n5\n");

        assertTrue(result.contains("Return confirmed successfully."), result);
        assertTrue(result.contains("Goodbye."), result);

        // 5 rentals remain, so the list is shown again from page 1: its first row appears twice.
        assertEquals(2, occurrences(result, "1) Item 1 - renter1"), result);

        long stillActive = rentalService.getActiveRentals(owner.id()).size();
        assertEquals(5, stillActive);
    }
}
