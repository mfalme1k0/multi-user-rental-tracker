package com.rentaltracker.domain;

import com.rentaltracker.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Set;

// Item lifecycle as a state machine.
public enum ItemStatus {
    AVAILABLE("available"),
    RENTED("rented"),
    UNLISTED("unlisted");

    private final String dbValue;

    ItemStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** The exact text stored in {@code listed_items.status}; must match the CHECK constraint in schema.sql. */
    public String dbValue() {
        return dbValue;
    }
    public static ItemStatus fromDbValue(String value) {
        for (ItemStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown item status: " + value);
    }

    public Set<ItemStatus> allowedTransitions() {
        return switch (this) {
            case AVAILABLE -> EnumSet.of(RENTED, UNLISTED);
            case RENTED -> EnumSet.of(AVAILABLE, UNLISTED);
            case UNLISTED -> EnumSet.of(AVAILABLE);
        };
    }

    public boolean canTransitionTo(ItemStatus target) {
        return allowedTransitions().contains(target);
    }

    /** @return {@code target} if the move is legal. @throws InvalidStateTransitionException otherwise. */
    public ItemStatus transitionTo(ItemStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Cannot change an item from " + dbValue + " to " + target.dbValue);
        }
        return target;
    }
}
