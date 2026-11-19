package com.rentaltracker.domain;

/** ACTIVE while the item is out, CLOSED once returned. "Overdue" is deliberately not a status: it is derived. */
public enum RentalStatus {
    ACTIVE("active"),
    CLOSED("closed");

    private final String dbValue;

    RentalStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Must match the CHECK constraint on {@code rentals.status}. */
    public String dbValue() {
        return dbValue;
    }

    /** @throws IllegalArgumentException for unknown text (the repository wraps it in a MappingException). */
    public static RentalStatus fromDbValue(String value) {
        for (RentalStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown rental status: " + value);
    }
}
