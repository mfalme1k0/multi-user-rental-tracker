package com.rentaltracker.exception;

/**
 * A write broke a database rule. Abstract: callers (and tests) always deal with the <em>specific</em>
 * subtype, because the project requires each broken rule to surface as its own exception.
 *
 * @see UniqueConstraintException
 * @see ForeignKeyConstraintException
 * @see NotNullConstraintException
 * @see CheckConstraintException
 */
public abstract class ConstraintViolationException extends DatabaseException {

    protected ConstraintViolationException(String message) {
        super(message);
    }

    protected ConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
