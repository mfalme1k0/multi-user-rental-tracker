package com.rentaltracker.exception;

/** A write broke a NOT NULL rule (e.g. item without a name). */
public class NotNullConstraintException extends ConstraintViolationException {

    public NotNullConstraintException(String message) {
        super(message);
    }

    public NotNullConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
