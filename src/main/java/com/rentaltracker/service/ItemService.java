package com.rentaltracker.service;

import com.rentaltracker.domain.*;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.InvalidStateTransitionException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.domain.Page;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;

import com.rentaltracker.validation.ItemValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Listing, inventory, delist/relist
public class ItemService {

    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final UserService userService;
    private final Transactor transactor;
    private final ItemValidator itemValidator;
    private final AppLogger logger;

    public ItemService(ItemRepository itemRepository,
                       RentalRepository rentalRepository,
                       UserService userService,
                       Transactor transactor,
                       ItemValidator itemValidator, AppLogger logger) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.rentalRepository = Objects.requireNonNull(rentalRepository);
        this.userService = Objects.requireNonNull(userService);
        this.transactor = Objects.requireNonNull(transactor);
        this.itemValidator = Objects.requireNonNull(itemValidator);
        this.logger = Objects.requireNonNull(logger);
    }

    // Creates a new listing for the specified owner after validating the item name and daily rental cost.
    // New listings start with AVAILABLE status.
    public Item listItem(long ownerId, String name,
                         String description, BigDecimal costPerDay) {

        String cleanName;
        BigDecimal validCost;

        try {
            cleanName = itemValidator.validateName(name);
            validCost = itemValidator.validateCostPerDay(costPerDay);
        } catch (ValidationException e) {
            logger.warn(ownerId,
                    "validation.failed",
                    Map.of(
                            "entity", "Item",
                            "operation", "list",
                            "reason", e.getMessage()
                    )
            );

            throw e;
        }

        return transactor.inTransaction(() -> {
            // Verify that the owner exists before creating the listing.
            User owner = userService.getUserById(ownerId);

            Item savedItem = itemRepository.insert(
                    Item.newListing(owner.id(), cleanName, description, validCost));

            logger.info(
                    owner.id(),
                    "item.listed",
                    Map.of("item_id", savedItem.id(),
                            "cost_per_day", savedItem.costPerDay()
                    )
            );

            return savedItem;
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

    // Returns a page of available listings owned by other users.
    // The current user's own listings and unavailable items are excluded.
    public Page<Item> getMarketplaceListings(
            long actingUserId, int pageNumber, int pageSize) {

        // Verify that the acting user exists before querying the marketplace.
        userService.getUserById(actingUserId);

        return itemRepository.findAvailableExcludingOwner(actingUserId, pageNumber, pageSize);
    }

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

            Item unlisted;

            try {
                unlisted = item.transitionTo(ItemStatus.UNLISTED);
            } catch (InvalidStateTransitionException exception) {
                logger.warn(requestingUserId,
                        "item.delist_rejected",
                        Map.of(
                                "item_id", itemId,
                                "current_status", item.status().name(),
                                "requested_status", ItemStatus.UNLISTED.name(),
                                "reason", "invalid_status_transition"
                        )
                );

                throw exception;
            }

            itemRepository.updateStatus(itemId, unlisted.status());

            logger.info(
                    requestingUserId,
                    "item.delisted",
                    Map.of("item_id", itemId)
            );

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
                logger.warn(requestingUserId,
                        "item.relist_rejected",
                        Map.of(
                                "item_id", itemId,
                                "reason", "not_owner"
                        )
                );

                throw new BusinessRuleException("You can only relist your own items");
            }

            Optional<Rental> activeRental = rentalRepository.findActiveByItemId(itemId);

            if (activeRental.isPresent()) {
                logger.warn(requestingUserId,
                        "item.relist_rejected",
                        Map.of(
                                "item_id", itemId,
                                "reason", "active_rental"
                        )
                );

                throw new BusinessRuleException(
                        "Cannot relist item " + itemId + ": it is still out on an active rental"
                );
            }

            Item available;

            try {
                available = item.transitionTo(ItemStatus.AVAILABLE);
            } catch (InvalidStateTransitionException e) {
                logger.warn(requestingUserId,
                        "item.relist_rejected",
                        Map.of("item_id", itemId,
                                "current_status", item.status().name(),
                                "requested_status", ItemStatus.AVAILABLE.name(),
                                "reason", "invalid_status_transition"
                        )
                );

                throw e;
            }

            itemRepository.updateStatus(itemId, available.status());

            logger.info(
                    requestingUserId,
                    "item.relisted",
                    Map.of("item_id", itemId)
            );

            return available;
        });
    }
}