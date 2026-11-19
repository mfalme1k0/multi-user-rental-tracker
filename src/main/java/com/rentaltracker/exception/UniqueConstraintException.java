package com.rentaltracker.exception;

/** A write broke a UNIQUE rule (e.g. duplicate username). */
public class UniqueConstraintException extends ConstraintViolationException {

    public UniqueConstraintException(String message) {
        super(message);
    }

    public UniqueConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
