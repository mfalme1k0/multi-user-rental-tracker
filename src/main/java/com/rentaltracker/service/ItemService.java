package com.rentaltracker.service;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemDetails;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Listing, inventory, delist/relist. */
public class ItemService {

    private final ItemRepository items;
    private final RentalRepository rentals;
    private final UserRepository users;
    private final Transactor transactor;

    public ItemService(ItemRepository items, RentalRepository rentals, UserRepository users, Transactor transactor) {
        this.items = Objects.requireNonNull(items);
        this.rentals = Objects.requireNonNull(rentals);
        this.users = Objects.requireNonNull(users);
        this.transactor = Objects.requireNonNull(transactor);
    }

    // Creates a listing with status AVAILABLE.

    public Item listItem(long ownerId, String name, String description, BigDecimal costPerDay) {
        String cleanName = Validation.requireNonBlank(name, "name");
        BigDecimal validCost = Validation.requirePositive(costPerDay);
        // description is free text and may legitimately be blank/absent, so it is not run through requireNonBlank
        return items.insert(Item.newListing(ownerId, cleanName, description, validCost));
    }

    /** Every item the owner has, whatever its status (the inventory screen). */
    public List<Item> getInventory(long ownerId) {
        return items.findByOwnerId(ownerId);
    }

    /** Only AVAILABLE items: the "record a rental" list, since only those can legally be rented. */
    public List<Item> getAvailableItems(long ownerId) {
        return items.findByOwnerIdAndStatus(ownerId, ItemStatus.AVAILABLE);
    }

    /**
     * Item plus its owner's username, for the item detail screen. throws com.rentaltracker.exception.NotFoundException no item with this id
     */
    public ItemDetails getDetails(long itemId) {
        Item item = items.getById(itemId);
        String ownerUsername = users.getById(item.ownerId()).username();
        return new ItemDetails(item, ownerUsername);
    }

    // AVAILABLE or RENTED -> UNLISTED. An item delisted while rented stays "out"; its active rental is untouched
    public Item delist(long itemId) {
        Item item = items.getById(itemId);
        Item unlisted = item.transitionTo(ItemStatus.UNLISTED);
        items.updateStatus(itemId, unlisted.status());
        return unlisted;
    }

    // UNLISTED -> AVAILABLE, only when the item has no active rental.

    public Item relist(long itemId) {
        return transactor.inTransaction(() -> {
            Item item = items.getById(itemId);
            Optional<Rental> activeRental = rentals.findActiveByItemId(itemId);
            if (activeRental.isPresent()) {
                throw new BusinessRuleException(
                        "Cannot relist item " + itemId + ": it is still out on an active rental");
            }
            Item available = item.transitionTo(ItemStatus.AVAILABLE);
            items.updateStatus(itemId, available.status());
            return available;
        });
    }
}
