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
    private final Clock clock;

    public RentalService(ItemRepository itemRepository, RentalRepository rentalRepository, UserService userService,
                         Transactor transactor, Clock clock) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.rentalRepository = Objects.requireNonNull(rentalRepository);
        this.userService = Objects.requireNonNull(userService);
        this.transactor = Objects.requireNonNull(transactor);
        this.clock = Objects.requireNonNull(clock);
    }

    // Records a rental for an existing, authenticated user.
    // Checks ownership, moves the item from AVAILABLE to RENTED, and inserts
    // the rental with an end time calculated from the start time and duration.
    public Rental recordRental(long itemId, long renterId, int days) {
        int validDays = Validation.requireAtLeastMinimumDays(days);

        return transactor.inTransaction(() -> {
            Item item = itemRepository.getById(itemId);
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
        return rentalRepository.findActiveDetailsByOwnerId(ownerId);
    }

    // Confirms a return: closes the rental with a return timestamp, and puts the item back to AVAILABLE
    public Rental confirmReturn(long rentalId) {
        return transactor.inTransaction(() -> {
            Rental rental = rentalRepository.getById(rentalId);
            if (rental.status() != RentalStatus.ACTIVE) {
                throw new BusinessRuleException("Rental " + rentalId + " is not active, it cannot be returned");
            }

            LocalDateTime returnedAt = LocalDateTime.now(clock);
            Rental closed = rental.closedAt(returnedAt);
            rentalRepository.updateStatus(rentalId, closed.status(), returnedAt);

            Item item = itemRepository.getById(rental.itemId());
            if (item.status() == ItemStatus.RENTED) {
                Item available = item.transitionTo(ItemStatus.AVAILABLE);
                itemRepository.updateStatus(item.id(), available.status());
            }

            // else: item is UNLISTED (delisted while out) - leave it as the owner set it.
            return closed;
        });
    }
}
