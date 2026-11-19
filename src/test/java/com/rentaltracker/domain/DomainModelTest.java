package com.rentaltracker.domain;

import com.rentaltracker.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainModelTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 16, 14, 30);

    @Test
    void newUserHasNoIdPasswordOrTimestamp() {
        User user = User.newUser("liisa");
        assertNull(user.id());
        assertNull(user.password());
        assertNull(user.createdAt());
        assertEquals("liisa", user.username());
    }

    @Test
    void newListingIsAvailableAndUnsaved() {
        Item item = Item.newListing(1L, "Ladder", "6-step aluminium ladder", BigDecimal.valueOf(5));
        assertNull(item.id());
        assertNull(item.createdAt());
        assertEquals(ItemStatus.AVAILABLE, item.status());
        assertEquals(BigDecimal.valueOf(5), item.costPerDay());
    }

    @Test
    void recordsAcceptInvalidValuesSoTestsCanProveTheDatabaseRejectsThem() {
        Item nameless = Item.newListing(1L, null, "no name", BigDecimal.valueOf(5));
        assertNull(nameless.name());
    }

    @Test
    void transitionToAppliesTheStateMachine() {
        Item rented = Item.newListing(1L, "Ladder", "d", BigDecimal.valueOf(5)).transitionTo(ItemStatus.RENTED);
        assertEquals(ItemStatus.RENTED, rented.status());
        assertThrows(InvalidStateTransitionException.class, () -> rented.transitionTo(ItemStatus.RENTED));
    }

    @Test
    void withStatusDoesNotCheckRulesAndKeepsOtherFields() {
        Item item = new Item(7L, 1L, "Drill", "cordless", BigDecimal.valueOf(8), ItemStatus.UNLISTED, START);
        Item copy = item.withStatus(ItemStatus.RENTED);
        assertEquals(ItemStatus.RENTED, copy.status());
        assertEquals(7L, copy.id());
        assertEquals("Drill", copy.name());
        assertEquals(START, copy.createdAt());
    }

    @Test
    void newActiveRentalIsOpenWithNoReturnTime() {
        Rental rental = Rental.newActive(1L, 2L, START, START.plusDays(3));
        assertNull(rental.id());
        assertNull(rental.returnedAt());
        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(START.plusDays(3), rental.endTime());
    }

    @Test
    void closedAtMarksClosedStampsReturnAndKeepsTheRest() {
        Rental active = new Rental(9L, 1L, 2L, START, START.plusDays(3), null, RentalStatus.ACTIVE);
        LocalDateTime back = START.plusDays(2);
        Rental closed = active.closedAt(back);
        assertEquals(RentalStatus.CLOSED, closed.status());
        assertEquals(back, closed.returnedAt());
        assertEquals(9L, closed.id());
        assertEquals(START.plusDays(3), closed.endTime());
        assertEquals(RentalStatus.ACTIVE, active.status());
    }

    @Test
    void readModelsExposeTheirParts() {
        Item item = Item.newListing(1L, "Ladder", "d", BigDecimal.valueOf(5));
        assertEquals("liisa", new ItemDetails(item, "liisa").ownerUsername());
        Rental rental = Rental.newActive(1L, 2L, START, START.plusDays(1));
        RentalDetails details = new RentalDetails(rental, "Ladder", "meelis");
        assertEquals("Ladder", details.itemName());
        assertEquals("meelis", details.renterUsername());
        assertEquals(rental, details.rental());
    }
}
