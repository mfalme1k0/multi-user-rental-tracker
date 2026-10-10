package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.*;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.exception.ForeignKeyConstraintException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class SQLiteRentalRepositoryTest {

    private DatabaseManager databaseManager;
    private SQLiteUserRepository userRepository;
    private SQLiteItemRepository itemRepository;
    private SQLiteRentalRepository rentalRepository;

    private Path databaseFile;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile =
                Files.createTempFile("rental-tracker-test-", ".db");

        databaseManager =
                new DatabaseManager(databaseFile);

        databaseManager.initialize();

        userRepository =
                new SQLiteUserRepository(databaseManager);

        itemRepository =
                new SQLiteItemRepository(databaseManager);

        rentalRepository =
                new SQLiteRentalRepository(databaseManager);
    }

    @Test
    void insertReturnsRentalWithGeneratedId() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        LocalDateTime startTime =
                LocalDateTime.of(2026, 9, 28, 10, 0);

        LocalDateTime endTime =
                LocalDateTime.of(2026, 10, 2, 10, 0);

        Rental rental = Rental.newActive(
                item.id(),
                renter.id(),
                startTime,
                endTime
        );

        Rental saved =
                rentalRepository.insert(rental);

        assertNotNull(saved.id());
        assertEquals(item.id(), saved.itemId());
        assertEquals(renter.id(), saved.renterId());
        assertEquals(startTime, saved.startTime());
        assertEquals(endTime, saved.endTime());
        assertNull(saved.returnedAt());
        assertEquals(RentalStatus.ACTIVE, saved.status());
    }

    @Test
    void findByIdReturnsSavedRental() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 2, 10, 0)
                )
        );

        Optional<Rental> found =
                rentalRepository.findById(rental.id());

        assertTrue(found.isPresent());
        assertEquals(rental, found.get());
    }

    @Test
    void findByIdReturnsEmptyWhenRentalDoesNotExist() {
        Optional<Rental> result =
                rentalRepository.findById(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getByIdReturnsSavedRental() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 2, 10, 0)
                )
        );

        Rental found =
                rentalRepository.getById(rental.id());

        assertEquals(rental, found);
    }

    @Test
    void getByIdThrowsWhenRentalDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> rentalRepository.getById(999L)
        );
    }

    @Test
    void insertRejectsSecondActiveRentalForSameItem() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter1 = userRepository.insert(
                User.newUser("renter1")
        );

        User renter2 = userRepository.insert(
                User.newUser("renter2")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter1.id(),
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );

        assertThrows(
                UniqueConstraintException.class,
                () -> rentalRepository.insert(
                        Rental.newActive(
                                item.id(),
                                renter2.id(),
                                LocalDateTime.of(2026, 9, 4, 10, 0),
                                LocalDateTime.of(2026, 9, 6, 10, 0)
                        )
                )
        );
    }

    @Test
    void findByItemIdReturnsRentalHistoryOldestFirst() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter1 = userRepository.insert(
                User.newUser("renter1")
        );

        User renter2 = userRepository.insert(
                User.newUser("renter2")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental firstRental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter1.id(),
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );

        LocalDateTime returnTime =
                LocalDateTime.of(2026, 9, 3, 10, 0);

        rentalRepository.updateStatus(
                firstRental.id(),
                RentalStatus.CLOSED,
                returnTime
        );

        Rental secondRental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter2.id(),
                        LocalDateTime.of(2026, 9, 10, 10, 0),
                        LocalDateTime.of(2026, 9, 12, 10, 0)
                )
        );

        List<Rental> rentals =
                rentalRepository.findByItemId(item.id());

        assertEquals(2, rentals.size());

        assertEquals(firstRental.id(), rentals.get(0).id());
        assertEquals(secondRental.id(), rentals.get(1).id());

        assertEquals(
                RentalStatus.CLOSED,
                rentals.get(0).status()
        );

        assertEquals(
                returnTime,
                rentals.get(0).returnedAt()
        );

        assertEquals(
                RentalStatus.ACTIVE,
                rentals.get(1).status()
        );

        assertNull(rentals.get(1).returnedAt());
    }

    @Test
    void findByItemIdReturnsEmptyWhenItemHasNoRentals() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        List<Rental> rentals =
                rentalRepository.findByItemId(item.id());

        assertTrue(rentals.isEmpty());
    }

    @Test
    void activeRentalUniqueIndexExists() throws Exception {
        try (Connection connection =
                     databaseManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT name
                             FROM sqlite_master
                             WHERE type = 'index'
                             AND name = 'idx_rentals_one_active_per_item'
                             """)) {

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(resultSet.next());
            }
        }
    }

    @Test
    void updateStatusClosesActiveRental() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );

        LocalDateTime returnTime =
                LocalDateTime.of(2026, 9, 3, 10, 0);

        rentalRepository.updateStatus(
                rental.id(),
                RentalStatus.CLOSED,
                returnTime
        );

        Rental updated =
                rentalRepository.getById(rental.id());

        assertEquals(RentalStatus.CLOSED, updated.status());
        assertEquals(returnTime, updated.returnedAt());
    }

    @Test
    void findActiveByItemIdReturnsActiveRental() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 2, 10, 0)
                )
        );

        Optional<Rental> result =
                rentalRepository.findActiveByItemId(item.id());

        assertTrue(result.isPresent());
        assertEquals(rental, result.get());
    }

    @Test
    void findActiveByItemIdReturnsEmptyWhenNoActiveRentalExists() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Optional<Rental> result =
                rentalRepository.findActiveByItemId(item.id());

        assertTrue(result.isEmpty());
    }

    @Test
    void findActiveDetailsByOwnerIdReturnsActiveRentalsForOwnersItemsOrderedByEndTime() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User otherOwner = userRepository.insert(
                User.newUser("other-owner")
        );

        User renter1 = userRepository.insert(
                User.newUser("alice")
        );

        User renter2 = userRepository.insert(
                User.newUser("bob")
        );

        User otherRenter = userRepository.insert(
                User.newUser("charlie")
        );

        Item camera = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Item bicycle = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Bicycle",
                        "Mountain bicycle",
                        new BigDecimal("25.00")
                )
        );

        Item laptop = itemRepository.insert(
                Item.newListing(
                        otherOwner.id(),
                        "Laptop",
                        "Work laptop",
                        new BigDecimal("75.00")
                )
        );

        Rental cameraRental = rentalRepository.insert(
                Rental.newActive(
                        camera.id(),
                        renter1.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 5, 10, 0)
                )
        );

        Rental bicycleRental = rentalRepository.insert(
                Rental.newActive(
                        bicycle.id(),
                        renter2.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 2, 10, 0)
                )
        );

        rentalRepository.insert(
                Rental.newActive(
                        laptop.id(),
                        otherRenter.id(),
                        LocalDateTime.of(2026, 9, 28, 10, 0),
                        LocalDateTime.of(2026, 10, 3, 10, 0)
                )
        );

        List<RentalDetails> results =
                rentalRepository.findActiveDetailsByOwnerId(owner.id());

        assertEquals(2, results.size());

        assertEquals(
                bicycleRental,
                results.get(0).rental()
        );
        assertEquals(
                "Bicycle",
                results.get(0).itemName()
        );
        assertEquals(
                "bob",
                results.get(0).renterUsername()
        );

        assertEquals(
                cameraRental,
                results.get(1).rental()
        );
        assertEquals(
                "Camera",
                results.get(1).itemName()
        );
        assertEquals(
                "alice",
                results.get(1).renterUsername()
        );
    }

    @Test
    void updateStatusClosesRental() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );

        LocalDateTime returnTime =
                LocalDateTime.of(2026, 9, 3, 10, 0);

        rentalRepository.updateStatus(
                rental.id(),
                RentalStatus.CLOSED,
                returnTime
        );

        Rental updated =
                rentalRepository.getById(rental.id());

        assertEquals(
                RentalStatus.CLOSED,
                updated.status()
        );

        assertEquals(
                returnTime,
                updated.returnedAt()
        );
    }

    @Test
    void updateStatusThrowsWhenRentalDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> rentalRepository.updateStatus(
                        999L,
                        RentalStatus.CLOSED,
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );
    }

    @Test
    void updateStatusAllowsNullReturnedAt() {
        User owner = userRepository.insert(
                User.newUser("owner")
        );

        User renter = userRepository.insert(
                User.newUser("renter")
        );

        Item item = itemRepository.insert(
                Item.newListing(
                        owner.id(),
                        "Camera",
                        "Digital camera",
                        new BigDecimal("100.50")
                )
        );

        Rental rental = rentalRepository.insert(
                Rental.newActive(
                        item.id(),
                        renter.id(),
                        LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 3, 10, 0)
                )
        );

        rentalRepository.updateStatus(
                rental.id(),
                RentalStatus.ACTIVE,
                null
        );

        Rental updated =
                rentalRepository.getById(rental.id());

        assertEquals(
                RentalStatus.ACTIVE,
                updated.status()
        );

        assertNull(updated.returnedAt());
    }
    @Test
    void rejectsRentalForNonexistentItem() {
        User renter = userRepository.insert(User.newUser("renter"));

        Rental rental = Rental.newActive(
                999L,                                   // no item with this id exists
                renter.id(),
                LocalDateTime.of(2026, 9, 28, 10, 0),
                LocalDateTime.of(2026, 9, 30, 10, 0)
        );

        assertThrows(
                ForeignKeyConstraintException.class,
                () -> rentalRepository.insert(rental)
        );
    }

    @Test
    void insertStoresReturnTimeOfAnAlreadyClosedRental() {
        User owner = userRepository.insert(User.newUser("owner"));
        User renter = userRepository.insert(User.newUser("renter"));
        Item item = itemRepository.insert(
                Item.newListing(owner.id(), "Camera", null, new BigDecimal("10")));
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime returned = LocalDateTime.of(2026, 9, 2, 8, 30);

        Rental saved = rentalRepository.insert(
                Rental.newActive(item.id(), renter.id(), start, start.plusDays(2))
                        .closedAt(returned));

        assertEquals(RentalStatus.CLOSED, saved.status());
        assertEquals(returned, saved.returnedAt());
        assertEquals(saved, rentalRepository.getById(saved.id()));
    }
}