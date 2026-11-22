package com.rentaltracker.repository;

import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.RentalStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence contract for rentals. No delete: closed rentals are history. */
public interface RentalRepository {

    /**
     * @return the saved rental with its id filled in.
     * @throws com.rentaltracker.exception.ForeignKeyConstraintException unknown item or renter
     */
    Rental insert(Rental rental);

    Optional<Rental> findById(long id);

    /** @throws com.rentaltracker.exception.NotFoundException when no row has this id */
    Rental getById(long id);

    /** Full history of one item, oldest first. */
    List<Rental> findByItemId(long itemId);

    /**
     * The ACTIVE rental of an item, if any. Drives the relist guard ("still out on rental?").
     * At most one can exist (partial unique index in schema.sql).
     */
    Optional<Rental> findActiveByItemId(long itemId);

    /**
     * Active rentals of the owner's items with item name and renter username, sorted by end time (due date)
     * ascending. Selected by RENTAL status, not item status: an item delisted while out is UNLISTED but its
     * rental is still active and must still be returnable.
     */
    List<RentalDetails> findActiveDetailsByOwnerId(long ownerId);

    /**
     * Sets the rental's status and return timestamp.
     *
     * @param returnedAt null when reopening/keeping active; the return time when closing
     * @throws com.rentaltracker.exception.NotFoundException when no row has this id
     */
    void updateStatus(long rentalId, RentalStatus status, LocalDateTime returnedAt);

    List<Rental> findMatches(long itemId,
                             long renterId,
                             LocalDateTime startTime);
}
