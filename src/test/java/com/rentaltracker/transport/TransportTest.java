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

class TransportTest {

    @TempDir
    Path tempDir;

    private final PrintStream originalOut = System.out;

    private SQLiteUserRepository users;
    private SQLiteItemRepository items;
    private SQLiteRentalRepository rentals;

    private UserService userService;
    private ItemService itemService;
    private RentalService rentalService;

    @BeforeEach
    void beforeEach() {
        DatabaseManager db = createDatabase();

        users = new SQLiteUserRepository(db);
        items = new SQLiteItemRepository(db);
        rentals = new SQLiteRentalRepository(db);

        SQLiteTransactor transactor = new SQLiteTransactor(db);

        userService = new UserService(users);

        itemService = new ItemService(
                items,
                rentals,
                users,
                transactor
        );

        rentalService = new RentalService(
                items,
                rentals,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );
    }

    @AfterEach
    void restoreSystemOutput() {
        System.setOut(originalOut);
    }

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");
        DatabaseManager databaseManager = new DatabaseManager(database);
        databaseManager.initialize();
        return databaseManager;
    }

    @Test
    void testFirstLaunchRegistersOwnerAndDisplaysWelcomeMessage() {
        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "  JACKSON  \n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        User owner = users.findFirst().orElseThrow();

        assertEquals("jackson", owner.username());

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Welcome, jackson!"));
        assertTrue(result.contains("=== Rental tracker ==="));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testStartingWithExistingOwnerWelcomesBackAndShowsMenu() {
        User existingOwner = users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Welcome back, jackson!"));
        assertTrue(result.contains("=== Rental tracker ==="));
        assertTrue(result.contains("Goodbye."));
        assertEquals(
                existingOwner.id(),
                users.findFirst().orElseThrow().id()
        );
    }

    @Test
    void testInvalidMenuOptionDisplaysError() {
        users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "99\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testListItemSuccessfully() {
        User owner = users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "1\nLaptop\nGaming laptop\n500\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        Item item = items.findByOwnerId(owner.id())
                .stream()
                .findFirst()
                .orElseThrow();

        assertEquals(owner.id(), item.ownerId());
        assertEquals("Laptop", item.name());
        assertEquals("Gaming laptop", item.description());
        assertEquals(new BigDecimal("500"), item.costPerDay());
        assertEquals(ItemStatus.AVAILABLE, item.status());

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== List an item ==="));
        assertTrue(result.contains("Item listed successfully."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testListItemWithInvalidCostDisplaysError() {
        users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "1\nLaptop\nGaming laptop\nnot-a-number\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid cost."));
        assertTrue(result.contains("Goodbye."));
        assertTrue(items.findByOwnerId(1).isEmpty());
    }

    @Test
    void testListItemWhenServiceRejectsCostDisplaysBusinessError() {
        users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "1\nLaptop\nGaming laptop\n0\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Goodbye."));
        assertTrue(items.findByOwnerId(1).isEmpty());
    }

    @Test
    void testViewInventoryWithNoItemsDisplaysEmptyMessage() {
        User owner = users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== My inventory ==="));
        assertTrue(result.contains("No items found."));
        assertTrue(result.contains("Goodbye."));
        assertTrue(items.findByOwnerId(owner.id()).isEmpty());
    }

    @Test
    void testViewInventoryDisplaysItems() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== My inventory ==="));
        assertTrue(result.contains("1) Laptop - available"));
        assertTrue(result.contains("B) Back to menu"));
        assertTrue(result.contains("Goodbye."));
        assertEquals(item.id(), items.getById(item.id()).id());
    }

    @Test
    void testViewInventorySelectsItemAndDisplaysDetails() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream("2\n1\n99\nb\nB\n5\n".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== Laptop ==="));
        assertTrue(result.contains("description    Gaming laptop"));
        assertTrue(result.contains("cost per day   500"));
        assertTrue(result.contains("status         available"));
        assertTrue(result.contains("owner          jackson"));
        assertTrue(result.contains("Delist"));
        assertTrue(result.contains("2) Back to list"));
        assertTrue(result.contains("Invalid option."));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.getById(item.id()).status()
        );
    }


    @Test
    void testDelistAvailableItem() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n1\n1\n2\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Item delisted."));
        assertTrue(result.contains("status         unlisted"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.UNLISTED,
                items.getById(item.id()).status()
        );
    }

    @Test
    void testRelistUnlistedItem() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        itemService.delist(item.id());

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n1\n1\n2\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Item relisted."));
        assertTrue(result.contains("status         available"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.getById(item.id()).status()
        );
    }

    @Test
    void testRelistItemWithActiveRentalDisplaysBusinessError() {
        User owner = users.insert(User.newUser("jackson"));
        User renter = users.insert(User.newUser("renter"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        itemService.delist(item.id());

        rentals.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(3)
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n1\n1\n2\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Cannot relist item"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.UNLISTED,
                items.getById(item.id()).status()
        );
    }

    @Test
    void testViewInventoryPaginationAndInvalidSelections() {
        User owner = users.insert(User.newUser("jackson"));

        for (int i = 1; i <= 6; i++) {
            itemService.listItem(
                    owner.id(),
                    "Item " + i,
                    "Description " + i,
                    new BigDecimal("100")
            );
        }

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\nN\nN\nP\nP\nabc\n0\n99\nB\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("1) Item 1 - available"));
        assertTrue(result.contains("5) Item 5 - available"));
        assertTrue(result.contains("1) Item 6 - available"));
        assertTrue(result.contains("N) Next page"));
        assertTrue(result.contains("P) Previous page"));
        assertTrue(result.contains("Invalid number."));
        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalWithNoAvailableItemsDisplaysMessage() {
        users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("No available items."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalSuccessfully() {
        User owner = users.insert(User.newUser("jackson"));
        User renter = users.insert(User.newUser("alex"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\n1\nalex\n3\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        Rental rental = rentals.findByItemId(item.id())
                .stream()
                .findFirst()
                .orElseThrow();

        assertEquals(item.id(), rental.itemId());
        assertEquals(renter.id(), rental.renterId());
        assertEquals(RentalStatus.ACTIVE, rental.status());

        assertEquals(
                ItemStatus.RENTED,
                items.getById(item.id()).status()
        );

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Rental recorded successfully."));
        assertTrue(result.contains("Rental ID: " + rental.id()));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalWithInvalidDurationDisplaysError() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\n1\nalex\ninvalid\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid duration."));
        assertTrue(result.contains("Goodbye."));

        assertTrue(rentals.findByItemId(item.id()).isEmpty());

        assertEquals(
                ItemStatus.AVAILABLE,
                items.getById(item.id()).status()
        );
    }

    @Test
    void testRecordRentalWhenServiceRejectsRentalDisplaysBusinessError() {
        User owner = users.insert(User.newUser("jackson"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\n1\njackson\n3\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains(
                "The owner of an item cannot rent their own item"
        ));
        assertTrue(result.contains("Goodbye."));

        assertTrue(rentals.findByItemId(item.id()).isEmpty());

        assertEquals(
                ItemStatus.AVAILABLE,
                items.getById(item.id()).status()
        );
    }

    @Test
    void testConfirmReturnSuccessfully() {
        User owner = users.insert(User.newUser("jackson"));
        User renter = users.insert(User.newUser("alex"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        items.updateStatus(item.id(), ItemStatus.RENTED);

        Rental rental = rentals.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(3)
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream("4\n1\n99\nb\n1\n1\n5\n".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        Rental savedRental = rentals.findById(rental.id()).orElseThrow();

        assertEquals(
                RentalStatus.CLOSED,
                savedRental.status()
        );

        assertEquals(
                ItemStatus.AVAILABLE,
                items.getById(item.id()).status()
        );

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== Confirm a return ==="));
        assertTrue(result.contains("Laptop - alex"));
        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Return confirmed successfully."));
    }

    @Test
    void testConfirmReturnWithNoActiveRentalsDisplaysMessage() {
        users.insert(User.newUser("jackson"));

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "4\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("No active rentals."));
        assertTrue(result.contains("Goodbye."));
        assertTrue(rentals.findById(1).isEmpty());
    }

    @Test
    void testConfirmReturnPaginationAndInvalidSelections() {
        User owner = users.insert(User.newUser("jackson"));

        for (int i = 1; i <= 6; i++) {
            User renter = users.insert(
                    User.newUser("renter" + i)
            );

            Item item = itemService.listItem(
                    owner.id(),
                    "Item " + i,
                    "Description " + i,
                    new BigDecimal("100")
            );

            items.updateStatus(item.id(), ItemStatus.RENTED);

            rentals.insert(
                    Rental.newActive(
                            item.id(),
                            renter.id(),
                            LocalDateTime.now(),
                            LocalDateTime.now().plusDays(3)
                    )
            );
        }

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "4\nN\nN\nP\nP\nabc\n0\n99\nB\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("1) Item 1 - renter1"));
        assertTrue(result.contains("1) Item 6 - renter6"));
        assertTrue(result.contains("N) Next page"));
        assertTrue(result.contains("P) Previous page"));
        assertTrue(result.contains("Invalid number."));
        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalPaginationAndInvalidSelections() {
        User owner = users.insert(User.newUser("jackson"));

        for (int i = 1; i <= 6; i++) {
            itemService.listItem(
                    owner.id(),
                    "Item " + i,
                    "Description " + i,
                    new BigDecimal("100")
            );
        }

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\nN\nN\nP\nP\nabc\n0\n99\nB\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("1) Item 1 - 100.00/day"));
        assertTrue(result.contains("5) Item 5 - 100.00/day"));
        assertTrue(result.contains("1) Item 6 - 100.00/day"));
        assertTrue(result.contains("N) Next page"));
        assertTrue(result.contains("P) Previous page"));
        assertTrue(result.contains("Invalid number."));
        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testConfirmReturnWhenRentalIsNoLongerActiveDisplaysBusinessError() {
        User owner = users.insert(User.newUser("jackson"));
        User renter = users.insert(User.newUser("alex"));

        Item item = itemService.listItem(
                owner.id(),
                "Laptop",
                "Gaming laptop",
                new BigDecimal("500")
        );

        Rental rental = rentalService.recordRental(
                item.id(),
                renter.username(),
                3
        );

        InputStream input = new InputStream() {
            private final ByteArrayInputStream source =
                    new ByteArrayInputStream(
                            "4\n1\n1\n2\n5\n"
                                    .getBytes(StandardCharsets.UTF_8)
                    );

            private int oneCount = 0;

            @Override
            public int read() {
                int value = source.read();

                if (value == '1') {
                    oneCount++;

                    if (oneCount == 2) {
                        rentals.updateStatus(
                                rental.id(),
                                RentalStatus.CLOSED,
                                LocalDateTime.now()
                        );
                    }
                }

                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (length == 0) {
                    return 0;
                }

                int value = read();

                if (value == -1) {
                    return -1;
                }

                buffer[offset] = (byte) value;
                return 1;
            }
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                rentalService
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(
                result.contains(
                        "Rental " + rental.id()
                                + " is not active, it cannot be returned"
                )
        );

        assertEquals(
                RentalStatus.CLOSED,
                rentals.findById(rental.id()).orElseThrow().status()
        );
    }

}