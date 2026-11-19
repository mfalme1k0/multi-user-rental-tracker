package com.rentaltracker.domain;

import com.rentaltracker.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.rentaltracker.domain.ItemStatus.AVAILABLE;
import static com.rentaltracker.domain.ItemStatus.RENTED;
import static com.rentaltracker.domain.ItemStatus.UNLISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the whole 3x3 transition matrix, so any change to the state machine is a deliberate, visible edit. */
class ItemStatusTest {

    @Test
    void availableMayBeRentedOrUnlisted() {
        assertEquals(EnumSet.of(RENTED, UNLISTED), AVAILABLE.allowedTransitions());
    }

    @Test
    void rentedMayBeReturnedOrDelistedWhileOut() {
        assertEquals(EnumSet.of(AVAILABLE, UNLISTED), RENTED.allowedTransitions());
    }

    @Test
    void unlistedMayOnlyBeRelisted() {
        assertEquals(EnumSet.of(AVAILABLE), UNLISTED.allowedTransitions());
    }

    @Test
    void noStateMayTransitionToItself() {
        for (ItemStatus status : ItemStatus.values()) {
            assertFalse(status.canTransitionTo(status), status + " -> itself");
        }
    }

    @Test
    void unlistedCanNeverBeRentedDirectly() {
        assertFalse(UNLISTED.canTransitionTo(RENTED));
        assertThrows(InvalidStateTransitionException.class, () -> UNLISTED.transitionTo(RENTED));
    }

    @Test
    void alreadyRentedCannotBeRentedAgain() {
        assertThrows(InvalidStateTransitionException.class, () -> RENTED.transitionTo(RENTED));
    }

    @Test
    void legalTransitionReturnsTheTarget() {
        assertEquals(RENTED, AVAILABLE.transitionTo(RENTED));
        assertEquals(UNLISTED, RENTED.transitionTo(UNLISTED));
        assertEquals(AVAILABLE, UNLISTED.transitionTo(AVAILABLE));
    }

    @Test
    void illegalTransitionMessageNamesBothStates() {
        InvalidStateTransitionException ex =
                assertThrows(InvalidStateTransitionException.class, () -> UNLISTED.transitionTo(RENTED));
        assertTrue(ex.getMessage().contains("unlisted"));
        assertTrue(ex.getMessage().contains("rented"));
    }

    @Test
    void dbValuesMatchTheSchemaCheckConstraintAndRoundTrip() {
        assertEquals("available", AVAILABLE.dbValue());
        assertEquals("rented", RENTED.dbValue());
        assertEquals("unlisted", UNLISTED.dbValue());
        for (ItemStatus status : ItemStatus.values()) {
            assertEquals(status, ItemStatus.fromDbValue(status.dbValue()));
        }
    }

    @Test
    void unknownDbValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ItemStatus.fromDbValue("lost"));
        assertThrows(IllegalArgumentException.class, () -> ItemStatus.fromDbValue(null));
    }

    @Test
    void allowedTransitionsIsNeverEmptySoNoStateIsADeadEnd() {
        for (ItemStatus status : ItemStatus.values()) {
            Set<ItemStatus> next = status.allowedTransitions();
            assertFalse(next.isEmpty(), status + " has no exit");
        }
    }
}
