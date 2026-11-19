package com.rentaltracker.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalStatusTest {

    @Test
    void dbValuesMatchTheSchemaCheckConstraintAndRoundTrip() {
        assertEquals("active", RentalStatus.ACTIVE.dbValue());
        assertEquals("closed", RentalStatus.CLOSED.dbValue());
        for (RentalStatus status : RentalStatus.values()) {
            assertEquals(status, RentalStatus.fromDbValue(status.dbValue()));
        }
    }

    @Test
    void unknownDbValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RentalStatus.fromDbValue("overdue"));
        assertThrows(IllegalArgumentException.class, () -> RentalStatus.fromDbValue(null));
    }
}
