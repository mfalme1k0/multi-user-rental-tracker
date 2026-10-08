package com.rentaltracker.service;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.InvalidStateTransitionException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.sqlite.SQLiteItemRepository;
import com.rentaltracker.repository.sqlite.SQLiteRentalRepository;
import com.rentaltracker.repository.sqlite.SQLiteTransactor;
import com.rentaltracker.repository.sqlite.SQLiteUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentalServiceTest {

    // Fixed clock so "end = start + days" is checked against an exact instant, not System.currentTimeMillis().
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-16T14:30:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime EXPECTED_START = LocalDateTime.of(2026, 6, 16, 14, 30);

    @TempDir
    Path tempDir;

    private SQLiteItemRepository items;
    private RentalService service;
    private ItemService itemService;
    private long ownerId;

    @BeforeEach
    void setUp() {
        DatabaseManager db = createDatabase();
        items = new SQLiteItemRepository(db);
        SQLiteRentalRepository rentals = new SQLiteRentalRepository(db);
        SQLiteUserRepository users = new SQLiteUserRepository(db);
        SQLiteTransactor transactor = new SQLiteTransactor(db);
        UserService userService = new UserService(users);
        itemService = new ItemService(items, rentals, users, transactor);
        service = new RentalService(items, rentals, userService, transactor, FIXED_CLOCK);
        ownerId = users.insert(User.newUser("owner")).id();
    }

    @Test
    void recordRentalMovesItemToRentedAndSetsEndTime() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);

        Rental rental = service.recordRental(item.id(), "renter", 3);

        assertEquals(ItemStatus.RENTED, items.getById(item.id()).status());
        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(EXPECTED_START, rental.startTime());
        assertEquals(EXPECTED_START.plusDays(3), rental.endTime());
    }

    @Test
    void recordRentalRejectsTheOwnerRentingTheirOwnItem() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);

        BusinessRuleException ex =
                assertThrows(BusinessRuleException.class, () -> service.recordRental(item.id(), "owner", 2));
        assertTrue(ex.getMessage().contains("own item"));
        // and the item must NOT have been mutated by the rejected attempt
        assertEquals(ItemStatus.AVAILABLE, items.getById(item.id()).status());
    }

    @Test
    void recordRentalRejectsDurationBelowOneDay() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        assertThrows(ValidationException.class, () -> service.recordRental(item.id(), "renter", 0));
    }

    @Test
    void recordRentalRejectsBlankRenterName() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        assertThrows(ValidationException.class, () -> service.recordRental(item.id(), " ", 1));
    }

    @Test
    void recordRentalRejectsAnItemThatIsAlreadyRented() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        service.recordRental(item.id(), "renter1", 1);
        assertThrows(InvalidStateTransitionException.class,
                () -> service.recordRental(item.id(), "renter2", 1));
    }

    @Test
    void confirmReturnClosesRentalAndMakesItemAvailableAgain() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        Rental rental = service.recordRental(item.id(), "renter", 2);

        Rental closed = service.confirmReturn(rental.id());

        assertEquals(RentalStatus.CLOSED, closed.status());
        assertEquals(EXPECTED_START, closed.returnedAt());
        assertEquals(ItemStatus.AVAILABLE, items.getById(item.id()).status());
    }

    @Test
    void confirmReturnLeavesAnItemDelistedWhileOutAsUnlisted() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        Rental rental = service.recordRental(item.id(), "renter", 2);
        itemService.delist(item.id()); // RENTED -> UNLISTED while still out

        service.confirmReturn(rental.id());

        assertEquals(ItemStatus.UNLISTED, items.getById(item.id()).status());
    }

    @Test
    void confirmReturnRejectsAnAlreadyClosedRental() {
        Item item = itemService.listItem(ownerId, "Ladder", "d", BigDecimal.ONE);
        Rental rental = service.recordRental(item.id(), "renter", 2);
        service.confirmReturn(rental.id());

        assertThrows(BusinessRuleException.class, () -> service.confirmReturn(rental.id()));
    }

    private DatabaseManager createDatabase() {
        Path database = tempDir.resolve("rental-tracker.db");
        DatabaseManager databaseManager = new DatabaseManager(database);
        databaseManager.initialize();
        return databaseManager;
    }
}
