package com.rentaltracker.exception;

/** A query (or an update) expected a row and found none. */
public class NotFoundException extends DatabaseException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
