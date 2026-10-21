package com.rentaltracker.service;

import com.rentaltracker.domain.*;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;

import com.rentaltracker.validation.ItemValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Listing, inventory, delist/relist
public class ItemService {

    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final UserService userService;
    private final Transactor transactor;
    private final ItemValidator itemValidator;

    public ItemService(ItemRepository itemRepository,
                       RentalRepository rentalRepository,
                       UserService userService,
                       Transactor transactor,
                       ItemValidator itemValidator) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.rentalRepository = Objects.requireNonNull(rentalRepository);
        this.userService = Objects.requireNonNull(userService);
        this.transactor = Objects.requireNonNull(transactor);
        this.itemValidator = Objects.requireNonNull(itemValidator);
    }

    // Creates a new listing for the specified owner after validating the item name and daily rental cost.
    // New listings start with AVAILABLE status.
    public Item listItem(long ownerId, String name,
                         String description, BigDecimal costPerDay) {

        String cleanName = itemValidator.validateName(name);
        BigDecimal validCost = itemValidator.validateCostPerDay(costPerDay);

        return transactor.inTransaction(() -> {
            // Verify that the owner exists before creating the listing.
            User owner = userService.getUserById(ownerId);

            return itemRepository.insert(
                    Item.newListing(owner.id(), cleanName, description, validCost));
        });
    }

    // Returns all items owned by the current user, including rented and unlisted items.
    public List<Item> getInventory(long ownerId) {
        // Verify that the owner exists before querying their listings.
        userService.getUserById(ownerId);

        return itemRepository.findByOwnerId(ownerId);
    }

    // Returns only AVAILABLE items owned by the current user.
    // Rented and unlisted items are excluded because they cannot currently be rented.
    public List<Item> getAvailableItems(long ownerId) {
        // Verify that the owner exists before querying their listings.
        userService.getUserById(ownerId);

        return itemRepository.findByOwnerIdAndStatus(ownerId, ItemStatus.AVAILABLE);
    }

    // Retrieves an item's details together with its owner's username.
    // Throws NotFoundException if the item does not exist.
    public ItemDetails getDetails(long itemId) {
        Item item = itemRepository.getById(itemId);
        String ownerUsername = userService.getUserById(item.ownerId())
                .username();

        return new ItemDetails(item, ownerUsername);
    }

    // TODO
    // Available listings belonging to other users.
//    public List<Item> getMarketplaceListings(long actingUserId) {
//        return itemRepository.findAvailableExcludingOwner(actingUserId);
//    }

    // Removes an item from the available listings by transitioning it to UNLISTED.
    // An active rental is not canceled if the item is delisted while rented.
    // Only the item's owner may delist it.
    public Item delist(long itemId, long requestingUserId) {
        return transactor.inTransaction(() -> {
            // Verify that the requesting user exists before checking ownership.
            userService.getUserById(requestingUserId);

            Item item = itemRepository.getById(itemId);

            if (item.ownerId() != requestingUserId) {
                throw new BusinessRuleException("You can only delist your own items");
            }

            Item unlisted = item.transitionTo(ItemStatus.UNLISTED);
            itemRepository.updateStatus(itemId, unlisted.status());

            return unlisted;
        });
    }

    // UNLISTED -> AVAILABLE, only when the item has no active rental.
    // Only the item's owner may relist it, and relisting is rejected while an active rental exists.
    // The status change is performed within a transaction.
    public Item relist(long itemId, long requestingUserId) {
        return transactor.inTransaction(() -> {
            // Verify that the requesting user exists before checking ownership.
            userService.getUserById(requestingUserId);

            Item item = itemRepository.getById(itemId);

            if (item.ownerId() != requestingUserId) {
                throw new BusinessRuleException(
                        "You can only relist your own items");
            }

            Optional<Rental> activeRental =
                    rentalRepository.findActiveByItemId(itemId);

            if (activeRental.isPresent()) {
                throw new BusinessRuleException(
                        "Cannot relist item " + itemId
                                + ": it is still out on an active rental");
            }

            Item available = item.transitionTo(ItemStatus.AVAILABLE);
            itemRepository.updateStatus(itemId, available.status());

            return available;
        });
    }
}