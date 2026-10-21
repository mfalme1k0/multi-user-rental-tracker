package com.rentaltracker.repository;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for items. There is no delete: items are soft-removed through the UNLISTED status.
 * List methods return an empty list, never null.
 */
public interface ItemRepository {

    Item insert(Item item);

    Optional<Item> findById(long id);

    Item getById(long id);

    //  Every item the owner has, in any status, oldest first (the inventory screen).
    List<Item> findByOwnerId(long ownerId);

    //  The owner's items in one status, oldest first (e.g. AVAILABLE for the "record a rental" list).
    List<Item> findByOwnerIdAndStatus(long ownerId, ItemStatus status);

    //  Available listings owned by somebody other than the supplied user, using database-level paging.
    Page<Item> findAvailableExcludingOwner(long excludedOwnerId, int pageNumber, int pageSize);

    // Changes an item from AVAILABLE to RENTED only when it is still available.
    boolean markRentedIfAvailable(long itemId);

    void updateStatus(long itemId, ItemStatus status);
}
