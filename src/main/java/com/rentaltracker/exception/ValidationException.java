package com.rentaltracker.exception;

/** Input that is well-formed text but unacceptable (blank name, cost below 1, duration below 1 day...). */
public class ValidationException extends BusinessRuleException {

    public ValidationException(String message) {
        super(message);
    }
}
