package com.rentaltracker.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A listed item. Immutable: "changing" it returns a new instance.

public record Item(
        Long id,
        Long ownerId,
        String name,
        String description,
        BigDecimal costPerDay,
        ItemStatus status,
        LocalDateTime createdAt) {

    /** A freshly listed item: status AVAILABLE, per the spec ("listing sets an item to available"). */
    public static Item newListing(Long ownerId, String name, String description, BigDecimal costPerDay) {
        return new Item(null, ownerId, name, description, costPerDay, ItemStatus.AVAILABLE, null);
    }

    /** Plain copy with another status. No rule checking; used when mapping rows and in tests. */
    public Item withStatus(ItemStatus newStatus) {
        return new Item(id, ownerId, name, description, costPerDay, newStatus, createdAt);
    }

    /** Copy with the status changed through the state machine. @throws com.rentaltracker.exception.InvalidStateTransitionException */
    public Item transitionTo(ItemStatus target) {
        return withStatus(status.transitionTo(target));
    }
}
