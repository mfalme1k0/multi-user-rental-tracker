package com.rentaltracker.exception;

/** The database file cannot be opened (missing directory, wrong path, bad permissions). */
public class DatabaseConnectionException extends DatabaseException {

    public DatabaseConnectionException(String message) {
        super(message);
    }

    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
