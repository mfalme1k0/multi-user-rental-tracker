package com.rentaltracker.exception;

/**
 * Root of every failure that originates in persistence.
 *
 * <p>Unchecked on purpose: the repository layer catches {@code java.sql.SQLException} (checked) and
 * rethrows one of these, so no layer above has to declare {@code throws SQLException}, and the menu loop
 * needs a single catch block instead of one per screen.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
