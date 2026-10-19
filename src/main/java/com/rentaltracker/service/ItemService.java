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

// Listing, inventory, delist/relist
public class ItemService {

    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final Transactor transactor;

    public ItemService(ItemRepository itemRepository, RentalRepository rentalRepository, UserRepository userRepository, Transactor transactor) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
        this.rentalRepository = Objects.requireNonNull(rentalRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.transactor = Objects.requireNonNull(transactor);
    }

    // Creates a new listing for the specified owner after validating the item name and daily rental cost.
    // New listings start with AVAILABLE status.
    public Item listItem(long ownerId, String name, String description, BigDecimal costPerDay) {
        String cleanName = Validation.requireNonBlank(name, "name");
        BigDecimal validCost = Validation.requirePositive(costPerDay);

        // description is free text and may legitimately be blank/absent, so it is not run through requireNonBlank
        return itemRepository.insert(Item.newListing(ownerId, cleanName, description, validCost));
    }

    // Returns all items owned by the current user, including rented and unlisted items.
    public List<Item> getInventory(long ownerId) {
        return itemRepository.findByOwnerId(ownerId);
    }

    // Returns only AVAILABLE items owned by the current user.
    // Rented and unlisted items are excluded because they cannot currently be rented.
    public List<Item> getAvailableItems(long ownerId) {
        return itemRepository.findByOwnerIdAndStatus(ownerId, ItemStatus.AVAILABLE);
    }

    // Retrieves an item's details together with its owner's username.
    // Throws NotFoundException if the item does not exist.
    public ItemDetails getDetails(long itemId) {
        Item item = itemRepository.getById(itemId);
        String ownerUsername = userRepository.getById(item.ownerId())
                .username();

        return new ItemDetails(item, ownerUsername);
    }

    // Available listings belonging to other users.
//    public List<Item> getMarketplaceListings(long actingUserId) {
//        return itemRepository.findAvailableExcludingOwner(actingUserId);
//    }

    // Removes an item from the available listings by transitioning it to UNLISTED.
    // An active rental is not canceled if the item is delisted while rented.
    // Only the item's owner may delist it.
    public Item delist(long itemId, long actingUserId) {
        Item item = itemRepository.getById(itemId);

        if (item.ownerId() != actingUserId) {
            throw new BusinessRuleException("You can only delist your own items");
        }

        Item unlisted = item.transitionTo(ItemStatus.UNLISTED);
        itemRepository.updateStatus(itemId, unlisted.status());

        return unlisted;
    }

    // UNLISTED -> AVAILABLE, only when the item has no active rental.
    // Only the item's owner may relist it, and relisting is rejected while an active rental exists.
    // The status change is performed within a transaction.
    public Item relist(long itemId, long actingUserId) {
        return transactor.inTransaction(() -> {
            Item item = itemRepository.getById(itemId);

            if (item.ownerId() != actingUserId) {
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
