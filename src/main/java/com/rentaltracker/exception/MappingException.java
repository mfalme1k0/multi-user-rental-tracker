package com.rentaltracker.exception;

/** A database row cannot be turned into its domain object (wrong type, unexpected NULL, unknown status text). */
public class MappingException extends DatabaseException {

    public MappingException(String message) {
        super(message);
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
