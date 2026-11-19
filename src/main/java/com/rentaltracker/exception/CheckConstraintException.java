package com.rentaltracker.exception;

/** A write broke a CHECK rule (e.g. status outside available/rented/unlisted). */
public class CheckConstraintException extends ConstraintViolationException {

    public CheckConstraintException(String message) {
        super(message);
    }

    public CheckConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
