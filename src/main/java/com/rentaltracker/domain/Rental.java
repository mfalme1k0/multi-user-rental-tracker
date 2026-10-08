package com.rentaltracker.domain;

import java.time.LocalDateTime;

// One rental. Never deleted: closed rentals are the history of who had what and when.
public record Rental(
        Long id,
        Long itemId,
        Long renterId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime returnedAt,
        RentalStatus status) {

    public static Rental newActive(Long itemId, Long renterId, LocalDateTime startTime, LocalDateTime endTime) {
        return new Rental(null, itemId, renterId, startTime, endTime, null, RentalStatus.ACTIVE);
    }

    /** Copy marked CLOSED with the return timestamp. */
    public Rental closedAt(LocalDateTime returnTime) {
        return new Rental(id, itemId, renterId, startTime, endTime, returnTime, RentalStatus.CLOSED);
    }
}
