package com.rentaltracker.transport;

import com.rentaltracker.domain.*;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.fake.FakeItemRepository;
import com.rentaltracker.service.fake.FakeRentalRepository;
import com.rentaltracker.service.fake.FakeTransactor;
import com.rentaltracker.service.fake.FakeUserRepository;
import org.junit.jupiter.api.Test;

import com.rentaltracker.service.UserService;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreSystemOutput() {
        System.setOut(originalOut);
    }

    @Test
    void testFirstLaunchRegistersOwnerAndDisplaysWelcomeMessage() {
        ByteArrayInputStream input = new ByteArrayInputStream("  JACKSON  \n".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        FakeUserRepository userRepository = new FakeUserRepository();
        UserService userService = new UserService(userRepository);

        Transport transport = new Transport(
                userService,
                null,
                null
        );

        User owner = transport.firstLaunch();

        assertEquals("jackson", owner.username());
        assertEquals(
                "jackson",
                userRepository.findFirst().orElseThrow().username()
        );
        assertTrue(
                output.toString(StandardCharsets.UTF_8)
                        .contains("Welcome, jackson!")
        );
    }

    @Test
    void testStartingWithExistingOwnerWelcomesBackAndShowsMenu() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User existingOwner = userRepository.insert(User.newUser("jackson"));

        UserService userService = new UserService(userRepository);

        ByteArrayInputStream input = new ByteArrayInputStream(
                        "5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                null,
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Welcome back, jackson!"));
        assertTrue(result.contains("=== Rental tracker ==="));
        assertTrue(result.contains("Goodbye."));
        assertEquals(1, userRepository.findFirst().orElseThrow().id());
    }

    @Test
    void testInvalidMenuOptionDisplaysError() {
        FakeUserRepository userRepository = new FakeUserRepository();

        userRepository.insert(User.newUser("jackson"));

        UserService userService = new UserService(userRepository);

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "99\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                null,
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testListItemSuccessfully() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        ByteArrayInputStream input = new ByteArrayInputStream(
                        "1\nLaptop\nGaming laptop\n500\n5\n"
                                .getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                null
        );

        transport.start();

        Item item = itemRepository.findById(1).orElseThrow();

        assertEquals(owner.id(), item.ownerId());
        assertEquals("Laptop", item.name());
        assertEquals("Gaming laptop", item.description());
        assertEquals(new BigDecimal("500"), item.costPerDay());

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== List an item ==="));
        assertTrue(result.contains("Item listed successfully."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testListItemWithInvalidCostDisplaysError() {
        FakeUserRepository userRepository = new FakeUserRepository();
        userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid cost."));
        assertTrue(result.contains("Goodbye."));
        assertTrue(itemRepository.findById(1).isEmpty());
    }

    @Test
    void testListItemWhenServiceRejectsCostDisplaysBusinessError() {
        FakeUserRepository userRepository = new FakeUserRepository();
        userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Goodbye."));
        assertTrue(itemRepository.findById(1).isEmpty());
    }

    @Test
    void testViewInventoryWithNoItemsDisplaysEmptyMessage() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== My inventory ==="));
        assertTrue(result.contains("No items found."));
        assertTrue(result.contains("Goodbye."));

        assertTrue(itemRepository.findByOwnerId(owner.id()).isEmpty());
    }

    @Test
    void testViewInventoryDisplaysItems() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("=== My inventory ==="));
        assertTrue(result.contains("1) Laptop - available"));
        assertTrue(result.contains("B) Back to menu"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(item.id(), itemRepository.findById(item.id()).orElseThrow().id());
    }

    @Test
    void testViewInventorySelectsItemAndDisplaysDetails() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n1\n2\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                null
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

        assertEquals(
                ItemStatus.AVAILABLE,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testDelistAvailableItem() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Item delisted."));
        assertTrue(result.contains("status         unlisted"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.UNLISTED,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testRelistUnlistedItem() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        Item item = itemRepository.insert(
                new Item(
                        null,
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500"),
                        ItemStatus.UNLISTED,
                        null
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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Item relisted."));
        assertTrue(result.contains("status         available"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.AVAILABLE,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testRelistItemWithActiveRentalDisplaysBusinessError() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));
        User renter = userRepository.insert(User.newUser("renter"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        Item item = itemRepository.insert(
                new Item(
                        null,
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500"),
                        ItemStatus.UNLISTED,
                        null
                )
        );

        rentalRepository.insert(
                new Rental(
                        null,
                        item.id(),
                        renter.id(),
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(3),
                        null,
                        RentalStatus.ACTIVE
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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Cannot relist item"));
        assertTrue(result.contains("Goodbye."));

        assertEquals(
                ItemStatus.UNLISTED,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testViewInventoryWithNonNumericSelectionDisplaysError() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\nabc\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid number."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testViewInventoryWithOutOfRangeSelectionDisplaysError() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\n99\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Invalid option."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testViewInventoryPagination() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        for (int i = 1; i <= 6; i++) {
            itemRepository.insert(
                    Item.newListing(
                            owner.id(),
                            "Item " + i,
                            "Description " + i,
                            new BigDecimal("100")
                    )
            );
        }

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "2\nN\nP\nB\n5\n".getBytes(StandardCharsets.UTF_8)
                );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        System.setIn(input);
        System.setOut(new PrintStream(output));

        Transport transport = new Transport(
                userService,
                itemService,
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("1) Item 1 - available"));
        assertTrue(result.contains("5) Item 5 - available"));
        assertTrue(result.contains("1) Item 6 - available"));
        assertTrue(result.contains("N) Next page"));
        assertTrue(result.contains("P) Previous page"));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalWithNoAvailableItemsDisplaysMessage() {
        FakeUserRepository userRepository = new FakeUserRepository();
        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

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
                null
        );

        transport.start();

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("No available items."));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalSuccessfully() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));
        User renter = userRepository.insert(User.newUser("alex"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "3\n1\nalex\n3\n5\n".getBytes(StandardCharsets.UTF_8)
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

        Rental rental = rentalRepository.findById(1).orElseThrow();

        assertEquals(item.id(), rental.itemId());
        assertEquals(renter.id(), rental.renterId());
        assertEquals(RentalStatus.ACTIVE, rental.status());

        assertEquals(
                ItemStatus.RENTED,
                itemRepository.findById(item.id()).orElseThrow().status()
        );

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("Rental recorded successfully."));
        assertTrue(result.contains("Rental ID: 1"));
        assertTrue(result.contains("Goodbye."));
    }

    @Test
    void testRecordRentalWithInvalidDurationDisplaysError() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
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

        assertTrue(rentalRepository.findById(1).isEmpty());

        assertEquals(
                ItemStatus.AVAILABLE,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testRecordRentalWhenServiceRejectsRentalDisplaysBusinessError() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
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

        assertTrue(rentalRepository.findById(1).isEmpty());

        assertEquals(
                ItemStatus.AVAILABLE,
                itemRepository.findById(item.id()).orElseThrow().status()
        );
    }

    @Test
    void testConfirmReturnSuccessfully() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));
        User renter = userRepository.insert(User.newUser("alex"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Laptop",
                        "Gaming laptop",
                        new BigDecimal("500")
                )
        );

        itemRepository.updateStatus(item.id(), ItemStatus.RENTED);

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusDays(3)
                )
        );

        ByteArrayInputStream input =
                new ByteArrayInputStream(
                        "4\n1\n1\n5\n"
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

        Rental savedRental = rentalRepository.findById(rental.id()).orElseThrow();

        assertEquals(RentalStatus.CLOSED, savedRental.status());
        assertEquals(
                ItemStatus.AVAILABLE,
                itemRepository.findById(item.id()).orElseThrow().status()
        );

        String result = output.toString(StandardCharsets.UTF_8);

        System.out.println(result);

        assertTrue(result.contains("=== Confirm a return ==="));
        assertTrue(result.contains("item-" + item.id() + " - renter-" + renter.id()));
        assertTrue(result.contains("Return confirmed successfully."));
    }

    @Test
    void testConfirmReturnWithNoActiveRentalsDisplaysMessage() {
        FakeUserRepository userRepository = new FakeUserRepository();

        User owner = userRepository.insert(User.newUser("jackson"));

        FakeItemRepository itemRepository = new FakeItemRepository();
        FakeRentalRepository rentalRepository = new FakeRentalRepository();
        FakeTransactor transactor = new FakeTransactor();

        UserService userService = new UserService(userRepository);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userRepository,
                transactor
        );

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                Clock.systemDefaultZone()
        );

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

        assertTrue(rentalRepository.findById(1).isEmpty());
    }
}