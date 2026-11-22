package com.rentaltracker.service.importing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RejectedRecordTest {

    @Test
    void createsValidRejectedRecord() {
        RejectedRecord record = new RejectedRecord(
                "users.csv",
                2,
                "username must not be blank"
        );

        assertAll(
                () -> assertEquals("users.csv", record.fileName()),
                () -> assertEquals(2, record.lineNumber()),
                () -> assertEquals(
                        "username must not be blank",
                        record.reason()
                )
        );
    }

    @Test
    void rejectsNullFileName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord(null, 1, "invalid record")
        );

        assertEquals("fileName must not be blank", exception.getMessage());
    }

    @Test
    void rejectsBlankFileName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord("  ", 1, "invalid record")
        );
    }

    @Test
    void rejectsLineNumberBelowOne() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord("users.csv", 0, "invalid record")
        );

        assertEquals("lineNumber must be >= 1", exception.getMessage());
    }

    @Test
    void rejectsNegativeLineNumber() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord("users.csv", -1, "invalid record")
        );
    }

    @Test
    void rejectsNullReason() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord("users.csv", 1, null)
        );

        assertEquals("reason must not be blank", exception.getMessage());
    }

    @Test
    void rejectsBlankReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RejectedRecord("users.csv", 1, "  ")
        );
    }

    @Test
    void formatsRecordAsExpected() {
        RejectedRecord record = new RejectedRecord(
                "listed_items.csv",
                5,
                "cost_per_day must be positive"
        );

        assertEquals(
                "listed_items.csv line 5: cost_per_day must be positive",
                record.toString()
        );
    }
}
