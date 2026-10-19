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

    // Creates a listing with status AVAILABLE.
    public Item listItem(long ownerId, String name, String description, BigDecimal costPerDay) {
        String cleanName = Validation.requireNonBlank(name, "name");
        BigDecimal validCost = Validation.requirePositive(costPerDay);

        // description is free text and may legitimately be blank/absent, so it is not run through requireNonBlank
        return itemRepository.insert(Item.newListing(ownerId, cleanName, description, validCost));
    }

    // Every item the owner has, whatever its status (the inventory screen).
    public List<Item> getInventory(long ownerId) {
        return itemRepository.findByOwnerId(ownerId);
    }

    // Only AVAILABLE items: the "record a rental" list, since only those can legally be rented.
    public List<Item> getAvailableItems(long ownerId) {
        return itemRepository.findByOwnerIdAndStatus(ownerId, ItemStatus.AVAILABLE);
    }

    // Item plus its owner's username, for the item detail screen. throws NotFoundException no item with this id
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

    // AVAILABLE or RENTED -> UNLISTED.
    // An item delisted while rented stays "out"; its active rental is untouched
    // Only the item's owner may delist it.
    public Item delist(long itemId, long actingUserId) {
        Item item = itemRepository.getById(itemId);

        if (item.ownerId() != actingUserId) {
            throw new BusinessRuleException(
                    "You can only delist your own items");
        }

        Item unlisted = item.transitionTo(ItemStatus.UNLISTED);
        itemRepository.updateStatus(itemId, unlisted.status());

        return unlisted;
    }

    // UNLISTED -> AVAILABLE, only when the item has no active rental.
    public Item relist(long itemId) {
        return transactor.inTransaction(() -> {
            Item item = itemRepository.getById(itemId);
            Optional<Rental> activeRental = rentalRepository.findActiveByItemId(itemId);

            if (activeRental.isPresent()) {
                throw new BusinessRuleException(
                        "Cannot relist item " + itemId + ": it is still out on an active rental");
            }

            Item available = item.transitionTo(ItemStatus.AVAILABLE);
            itemRepository.updateStatus(itemId, available.status());

            return available;
        });
    }
}
