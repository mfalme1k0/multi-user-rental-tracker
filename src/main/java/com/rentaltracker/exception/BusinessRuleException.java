package com.rentaltracker.exception;

/**
 * A business rule was broken (as opposed to a database failure). Thrown by the service layer and by the
 * item state machine; the transport layer shows the message and returns to the menu.
 *
 * <p>Deliberately NOT a {@link DatabaseException}: "you can't rent a rented item" is not a persistence problem
 * and must not be handled like one.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
