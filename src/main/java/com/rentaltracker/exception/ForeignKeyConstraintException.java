package com.rentaltracker.exception;

/** A write broke a FOREIGN KEY rule (e.g. rental pointing to a non-existent item). */
public class ForeignKeyConstraintException extends ConstraintViolationException {

    public ForeignKeyConstraintException(String message) {
        super(message);
    }

    public ForeignKeyConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
