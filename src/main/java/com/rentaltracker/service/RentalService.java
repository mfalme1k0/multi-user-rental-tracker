package com.rentaltracker.service;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.validation.RentalValidator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

// Rent and return.
public class RentalService {

    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final UserService userService;
    private final Transactor transactor;
    private final RentalValidator rentalValidator;
    private final Clock clock;

    public RentalService(ItemRepository itemRepository, RentalRepository rentalRepository, UserService userService,
                         Transactor transactor, RentalValidator rentalValidator, Clock clock) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.rentalRepository = Objects.requireNonNull(rentalRepository);
        this.userService = Objects.requireNonNull(userService);
        this.transactor = Objects.requireNonNull(transactor);
        this.rentalValidator = Objects.requireNonNull(rentalValidator);
        this.clock = Objects.requireNonNull(clock);
    }

    // Records a rental for an existing, authenticated user.
    // Checks ownership, moves the item from AVAILABLE to RENTED, and inserts
    // the rental with an end time calculated from the start time and duration.
    public Rental recordRental(long itemId, long renterId, int days) {
        int validDays = rentalValidator.validateRentalDuration(days);

        return transactor.inTransaction(() -> {
            Item item = itemRepository.getById(itemId);

            // Verify that the renter exists before recording the rental.
            User renter = userService.getUserById(renterId);

            // A user cannot rent their own item.
            if (Objects.equals(renter.id(), item.ownerId())) {
                throw new BusinessRuleException(
                        "The owner of an item cannot rent their own item");
            }

            Item rentedItem = item.transitionTo(ItemStatus.RENTED);
            itemRepository.updateStatus(itemId, rentedItem.status());

            LocalDateTime start = LocalDateTime.now(clock);
            LocalDateTime end = start.plusDays(validDays);

            return rentalRepository.insert(
                    Rental.newActive(itemId, renter.id(), start, end));
        });
    }

    // Active rentals across the owner's items, the oldest due date first (the "confirm a return" list).
    public List<RentalDetails> getActiveRentals(long ownerId) {
        // Verify that the owner exists before querying their active rentals.
        userService.getUserById(ownerId);

        return rentalRepository.findActiveDetailsByOwnerId(ownerId);
    }

    // Confirms the return of an active rental.
    // Only the item's owner may confirm the return.
    // A returned item becomes AVAILABLE if it is still RENTED;
    // An item delisted while rented remains UNLISTED.
    public Rental confirmReturn(long rentalId, long requestingUserId) {
        return transactor.inTransaction(() -> {
            // Verify that the requesting user exists before checking ownership.
            userService.getUserById(requestingUserId);

            Rental rental = rentalRepository.getById(rentalId);
            Item item = itemRepository.getById(rental.itemId());

            if (item.ownerId() != requestingUserId) {
                throw new BusinessRuleException(
                        "You can only confirm returns for your own items");
            }

            if (rental.status() != RentalStatus.ACTIVE) {
                throw new BusinessRuleException(
                        "Rental " + rentalId + " is not active, it cannot be returned");
            }

            LocalDateTime returnedAt = LocalDateTime.now(clock);
            Rental closed = rental.closedAt(returnedAt);

            rentalRepository.updateStatus(rentalId, closed.status(), returnedAt);

            if (item.status() == ItemStatus.RENTED) {
                Item available = item.transitionTo(ItemStatus.AVAILABLE);
                itemRepository.updateStatus(item.id(), available.status());
            }

            // An UNLISTED item stays UNLISTED after its active rental is returned.
            return closed;
        });
    }
}
