package com.rentaltracker.exception;

/** An item status change that the state machine does not allow (e.g. rented -> rented, unlisted -> rented). */
public class InvalidStateTransitionException extends BusinessRuleException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
