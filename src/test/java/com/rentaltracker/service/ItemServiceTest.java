package com.rentaltracker.service;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemDetails;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.InvalidStateTransitionException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.domain.Page;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.validation.ItemValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long ITEM_ID = 10L;

    private static final BigDecimal COST =
            new BigDecimal("25.00");

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 1, 1, 10, 0);

    private ItemRepository itemRepository;
    private RentalRepository rentalRepository;
    private UserService userService;
    private Transactor transactor;
    private ItemValidator itemValidator;
    private AppLogger logger;

    private ItemService itemService;

    private User owner;
    private Item availableItem;
    private Item unlistedItem;
    private Item rentedItem;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        rentalRepository = mock(RentalRepository.class);
        userService = mock(UserService.class);
        transactor = mock(Transactor.class);
        itemValidator = mock(ItemValidator.class);
        logger = mock(AppLogger.class);

        itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                itemValidator,
                logger
        );

        owner = new User(
                OWNER_ID,
                "owner",
                "hashed-password",
                CREATED_AT
        );

        availableItem = new Item(
                ITEM_ID,
                OWNER_ID,
                "Camera",
                "Digital camera",
                COST,
                ItemStatus.AVAILABLE,
                CREATED_AT
        );

        unlistedItem = availableItem.withStatus(
                ItemStatus.UNLISTED
        );

        rentedItem = availableItem.withStatus(
                ItemStatus.RENTED
        );

        when(userService.getUserById(OWNER_ID))
                .thenReturn(owner);

        // Execute the work supplied to the transaction.
        when(transactor.inTransaction(
                org.mockito.ArgumentMatchers
                        .<Supplier<Item>>any()
        )).thenAnswer(invocation -> {
            Supplier<Item> work = invocation.getArgument(0);
            return work.get();
        });
    }

    // listItem tests
    @Test
    void testListItemCreatesAvailableListing() {
        when(itemValidator.validateName("Camera"))
                .thenReturn("Camera");

        when(itemValidator.validateCostPerDay(COST))
                .thenReturn(COST);

        Item savedItem = new Item(
                ITEM_ID,
                OWNER_ID,
                "Camera",
                "Digital camera",
                COST,
                ItemStatus.AVAILABLE,
                CREATED_AT
        );

        when(itemRepository.insert(any(Item.class)))
                .thenReturn(savedItem);

        Item result = itemService.listItem(
                OWNER_ID,
                "Camera",
                "Digital camera",
                COST
        );

        assertEquals(savedItem, result);

        verify(userService).getUserById(OWNER_ID);

        verify(itemRepository).insert(argThat(item ->
                item.id() == null
                        && item.ownerId().equals(OWNER_ID)
                        && item.name().equals("Camera")
                        && item.description().equals("Digital camera")
                        && item.costPerDay().compareTo(COST) == 0
                        && item.status() == ItemStatus.AVAILABLE
        ));

        verify(logger).info(
                eq(OWNER_ID),
                eq("item.listed"),
                anyMap()
        );
    }

    @Test
    void testListItemUsesValidatedNameAndCost() {
        BigDecimal validatedCost = new BigDecimal("30.00");

        when(itemValidator.validateName("  Camera  "))
                .thenReturn("Camera");

        when(itemValidator.validateCostPerDay(COST))
                .thenReturn(validatedCost);

        when(itemRepository.insert(any(Item.class)))
                .thenAnswer(invocation -> {
                    Item item = invocation.getArgument(0);

                    return new Item(
                            11L,
                            item.ownerId(),
                            item.name(),
                            item.description(),
                            item.costPerDay(),
                            item.status(),
                            CREATED_AT
                    );
                });

        Item result = itemService.listItem(
                OWNER_ID,
                "  Camera  ",
                "Digital camera",
                COST
        );

        assertEquals("Camera", result.name());
        assertEquals(0, validatedCost.compareTo(result.costPerDay()));

        verify(itemValidator).validateName("  Camera  ");
        verify(itemValidator).validateCostPerDay(COST);
    }

    @Test
    void testListItemRejectsInvalidName() {
        ValidationException exception =
                new ValidationException("Item name is required");

        when(itemValidator.validateName(" "))
                .thenThrow(exception);

        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> itemService.listItem(
                        OWNER_ID,
                        " ",
                        "Description",
                        COST
                )
        );

        assertSame(exception, thrown);

        verify(logger).warn(
                eq(OWNER_ID),
                eq("validation.failed"),
                anyMap()
        );

        verifyNoInteractions(userService, itemRepository, transactor);
        verify(itemValidator, never()).validateCostPerDay(any());
    }

    @Test
    void testListItemRejectsInvalidCost() {
        ValidationException exception =
                new ValidationException("Cost must be positive");

        when(itemValidator.validateName("Camera"))
                .thenReturn("Camera");

        when(itemValidator.validateCostPerDay(
                new BigDecimal("-1.00")
        )).thenThrow(exception);

        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> itemService.listItem(
                        OWNER_ID,
                        "Camera",
                        "Description",
                        new BigDecimal("-1.00")
                )
        );

        assertSame(exception, thrown);

        verify(logger).warn(
                eq(OWNER_ID),
                eq("validation.failed"),
                anyMap()
        );

        verifyNoInteractions(userService, itemRepository, transactor);
    }

    @Test
    void testListItemPropagatesOwnerLookupFailure() {
        when(itemValidator.validateName("Camera"))
                .thenReturn("Camera");

        when(itemValidator.validateCostPerDay(COST))
                .thenReturn(COST);

        RuntimeException exception =
                new RuntimeException("User lookup failed");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> itemService.listItem(
                        OWNER_ID,
                        "Camera",
                        "Description",
                        COST
                )
        );

        assertSame(exception, thrown);
        verify(itemRepository, never()).insert(any());
    }

    // getInventory tests
    @Test
    void testGetInventoryReturnsAllOwnedItems() {
        List<Item> items = List.of(
                availableItem,
                rentedItem,
                unlistedItem
        );

        when(itemRepository.findByOwnerId(OWNER_ID))
                .thenReturn(items);

        List<Item> result = itemService.getInventory(OWNER_ID);

        assertEquals(items, result);
        assertEquals(3, result.size());

        verify(userService).getUserById(OWNER_ID);
        verify(itemRepository).findByOwnerId(OWNER_ID);
    }

    @Test
    void testGetInventoryReturnsEmptyListWhenOwnerHasNoItems() {
        when(itemRepository.findByOwnerId(OWNER_ID))
                .thenReturn(List.of());

        List<Item> result = itemService.getInventory(OWNER_ID);

        assertTrue(result.isEmpty());

        verify(userService).getUserById(OWNER_ID);
        verify(itemRepository).findByOwnerId(OWNER_ID);
    }

    @Test
    void testGetInventoryDoesNotQueryItemsWhenOwnerLookupFails() {
        RuntimeException exception =
                new RuntimeException("User not found");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> itemService.getInventory(OWNER_ID)
        );

        assertSame(exception, thrown);
        verifyNoInteractions(itemRepository);
    }

    // getAvailableItems tests
    @Test
    void testGetAvailableItemsReturnsOnlyAvailableItems() {
        List<Item> items = List.of(availableItem);

        when(itemRepository.findByOwnerIdAndStatus(
                OWNER_ID,
                ItemStatus.AVAILABLE
        )).thenReturn(items);

        List<Item> result =
                itemService.getAvailableItems(OWNER_ID);

        assertEquals(items, result);
        assertTrue(result.stream().allMatch(
                item -> item.status() == ItemStatus.AVAILABLE
        ));

        verify(userService).getUserById(OWNER_ID);

        verify(itemRepository).findByOwnerIdAndStatus(
                OWNER_ID,
                ItemStatus.AVAILABLE
        );
    }

    @Test
    void testGetAvailableItemsReturnsEmptyListWhenNoneAreAvailable() {
        when(itemRepository.findByOwnerIdAndStatus(
                OWNER_ID,
                ItemStatus.AVAILABLE
        )).thenReturn(List.of());

        List<Item> result =
                itemService.getAvailableItems(OWNER_ID);

        assertTrue(result.isEmpty());

        verify(itemRepository).findByOwnerIdAndStatus(
                OWNER_ID,
                ItemStatus.AVAILABLE
        );
    }

    @Test
    void testGetAvailableItemsDoesNotQueryWhenOwnerLookupFails() {
        RuntimeException exception =
                new RuntimeException("User not found");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getAvailableItems(OWNER_ID)
                )
        );

        verifyNoInteractions(itemRepository);
    }

    // getDetails
    @Test
    void testGetDetailsReturnsItemAndOwnerUsername() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        ItemDetails result = itemService.getDetails(ITEM_ID);

        assertEquals(availableItem, result.item());
        assertEquals("owner", result.ownerUsername());

        verify(itemRepository).getById(ITEM_ID);
        verify(userService).getUserById(OWNER_ID);
    }

    @Test
    void testGetDetailsPropagatesItemNotFoundFailure() {
        RuntimeException exception =
                new RuntimeException("Item not found");

        when(itemRepository.getById(ITEM_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getDetails(ITEM_ID)
                )
        );

        verifyNoInteractions(userService);
    }

    @Test
    void testGetDetailsPropagatesOwnerLookupFailure() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        RuntimeException exception =
                new RuntimeException("Owner not found");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getDetails(ITEM_ID)
                )
        );
    }

    // getMarketplaceListings
    @Test
    void testGetMarketplaceListingsUsesUserAndPaginationArguments() {
        @SuppressWarnings("unchecked")
        Page<Item> expectedPage = mock(Page.class);

        when(itemRepository.findAvailableExcludingOwner(
                OTHER_USER_ID,
                2,
                5
        )).thenReturn(expectedPage);

        Page<Item> result =
                itemService.getMarketplaceListings(
                        OTHER_USER_ID,
                        2,
                        5
                );

        assertSame(expectedPage, result);

        verify(userService).getUserById(OTHER_USER_ID);

        verify(itemRepository).findAvailableExcludingOwner(
                OTHER_USER_ID,
                2,
                5
        );
    }

    @Test
    void testGetMarketplaceListingsReturnsEmptyPage() {
        @SuppressWarnings("unchecked")
        Page<Item> emptyPage = mock(Page.class);

        when(itemRepository.findAvailableExcludingOwner(
                OWNER_ID,
                0,
                10
        )).thenReturn(emptyPage);

        Page<Item> result =
                itemService.getMarketplaceListings(
                        OWNER_ID,
                        0,
                        10
                );

        assertSame(emptyPage, result);
    }

    @Test
    void testGetMarketplaceListingsDoesNotQueryWhenUserLookupFails() {
        RuntimeException exception =
                new RuntimeException("User not found");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getMarketplaceListings(
                                OWNER_ID,
                                0,
                                10
                        )
                )
        );

        verifyNoInteractions(itemRepository);
    }

    // delist
    @Test
    void testDelistChangesAvailableItemToUnlisted() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        Item result = itemService.delist(ITEM_ID, OWNER_ID);

        assertEquals(ItemStatus.UNLISTED, result.status());
        assertEquals(ITEM_ID, result.id());

        verify(itemRepository).updateStatus(
                ITEM_ID,
                ItemStatus.UNLISTED
        );

        verify(logger).info(
                eq(OWNER_ID),
                eq("item.delisted"),
                anyMap()
        );
    }

    @Test
    void testDelistAllowsRentedItemToBecomeUnlisted() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(rentedItem);

        Item result = itemService.delist(ITEM_ID, OWNER_ID);

        assertEquals(ItemStatus.UNLISTED, result.status());

        verify(itemRepository).updateStatus(
                ITEM_ID,
                ItemStatus.UNLISTED
        );
    }

    @Test
    void testDelistRejectsItemOwnedByAnotherUser() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> itemService.delist(ITEM_ID, OTHER_USER_ID)
        );

        assertEquals(
                "You can only delist your own items",
                exception.getMessage()
        );

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());

        verify(logger, never()).info(
                any(),
                eq("item.delisted"),
                anyMap()
        );
    }

    @Test
    void testDelistRejectsInvalidStatusTransition() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        assertThrows(
                InvalidStateTransitionException.class,
                () -> itemService.delist(ITEM_ID, OWNER_ID)
        );

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());

        verify(logger).warn(
                eq(OWNER_ID),
                eq("item.delist_rejected"),
                anyMap()
        );
    }

    @Test
    void testDelistPropagatesItemLookupFailure() {
        RuntimeException exception =
                new RuntimeException("Item not found");

        when(itemRepository.getById(ITEM_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.delist(ITEM_ID, OWNER_ID)
                )
        );

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());
    }

    // relist
    @Test
    void testRelistChangesUnlistedItemToAvailable() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        when(rentalRepository.findActiveByItemId(ITEM_ID))
                .thenReturn(Optional.empty());

        Item result = itemService.relist(ITEM_ID, OWNER_ID);

        assertEquals(ItemStatus.AVAILABLE, result.status());
        assertEquals(ITEM_ID, result.id());

        verify(rentalRepository).findActiveByItemId(ITEM_ID);

        verify(itemRepository).updateStatus(
                ITEM_ID,
                ItemStatus.AVAILABLE
        );

        verify(logger).info(
                eq(OWNER_ID),
                eq("item.relisted"),
                anyMap()
        );
    }

    @Test
    void testRelistRejectsItemOwnedByAnotherUser() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        assertThrows(
                BusinessRuleException.class,
                () -> itemService.relist(ITEM_ID, OTHER_USER_ID)
        );

        verifyNoInteractions(rentalRepository);

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());

        verify(logger).warn(
                eq(OTHER_USER_ID),
                eq("item.relist_rejected"),
                anyMap()
        );
    }

    @Test
    void testRelistRejectsItemWithActiveRental() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        Rental activeRental = Rental.newActive(
                ITEM_ID,
                OTHER_USER_ID,
                CREATED_AT,
                CREATED_AT.plusDays(3)
        );

        when(rentalRepository.findActiveByItemId(ITEM_ID))
                .thenReturn(Optional.of(activeRental));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> itemService.relist(ITEM_ID, OWNER_ID)
        );

        assertTrue(exception.getMessage().contains(
                "active rental"
        ));

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());

        verify(logger).warn(
                eq(OWNER_ID),
                eq("item.relist_rejected"),
                anyMap()
        );
    }

    @Test
    void testRelistRejectsInvalidStatusTransition() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        when(rentalRepository.findActiveByItemId(ITEM_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidStateTransitionException.class,
                () -> itemService.relist(ITEM_ID, OWNER_ID)
        );

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());

        verify(logger).warn(
                eq(OWNER_ID),
                eq("item.relist_rejected"),
                anyMap()
        );
    }

    @Test
    void testRelistPropagatesItemLookupFailure() {
        RuntimeException exception =
                new RuntimeException("Item not found");

        when(itemRepository.getById(ITEM_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.relist(ITEM_ID, OWNER_ID)
                )
        );

        verifyNoInteractions(rentalRepository);
        verify(itemRepository, never())
                .updateStatus(anyLong(), any());
    }

    @Test
    void testRelistPropagatesActiveRentalLookupFailure() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        RuntimeException exception =
                new RuntimeException("Rental lookup failed");

        when(rentalRepository.findActiveByItemId(ITEM_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.relist(ITEM_ID, OWNER_ID)
                )
        );

        verify(itemRepository, never())
                .updateStatus(anyLong(), any());
    }


    @Test
    void testListItemPropagatesRepositoryInsertionFailure() {
        when(itemValidator.validateName("Camera"))
                .thenReturn("Camera");
        when(itemValidator.validateCostPerDay(COST))
                .thenReturn(COST);

        RuntimeException exception =
                new RuntimeException("Insertion failed");

        when(itemRepository.insert(any(Item.class)))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> itemService.listItem(
                        OWNER_ID, "Camera", "Description", COST
                )
        );

        assertSame(exception, thrown);
        verify(transactor).inTransaction(
                org.mockito.ArgumentMatchers
                        .<Supplier<Item>>any()
        );
    }

    @Test
    void testGetInventoryPropagatesRepositoryFailure() {
        RuntimeException exception =
                new RuntimeException("Inventory query failed");

        when(itemRepository.findByOwnerId(OWNER_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getInventory(OWNER_ID)
                )
        );
    }

    @Test
    void testGetAvailableItemsPropagatesRepositoryFailure() {
        RuntimeException exception =
                new RuntimeException("Available-items query failed");

        when(itemRepository.findByOwnerIdAndStatus(
                OWNER_ID, ItemStatus.AVAILABLE
        )).thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getAvailableItems(OWNER_ID)
                )
        );
    }

    @Test
    void testGetMarketplaceListingsPropagatesRepositoryFailure() {
        RuntimeException exception =
                new RuntimeException("Marketplace query failed");

        when(itemRepository.findAvailableExcludingOwner(
                OWNER_ID, 0, 10
        )).thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.getMarketplaceListings(
                                OWNER_ID, 0, 10
                        )
                )
        );
    }

    @Test
    void testDelistPropagatesStatusUpdateFailure() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(availableItem);

        RuntimeException exception =
                new RuntimeException("Status update failed");

        doThrow(exception)
                .when(itemRepository)
                .updateStatus(ITEM_ID, ItemStatus.UNLISTED);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.delist(ITEM_ID, OWNER_ID)
                )
        );
    }

    @Test
    void testRelistPropagatesStatusUpdateFailure() {
        when(itemRepository.getById(ITEM_ID))
                .thenReturn(unlistedItem);

        when(rentalRepository.findActiveByItemId(ITEM_ID))
                .thenReturn(Optional.empty());

        RuntimeException exception =
                new RuntimeException("Status update failed");

        doThrow(exception)
                .when(itemRepository)
                .updateStatus(ITEM_ID, ItemStatus.AVAILABLE);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.relist(ITEM_ID, OWNER_ID)
                )
        );
    }

    @Test
    void testDelistDoesNotUpdateStatusWhenUserLookupFails() {
        RuntimeException exception =
                new RuntimeException("User lookup failed");

        when(userService.getUserById(OWNER_ID))
                .thenThrow(exception);

        assertSame(
                exception,
                assertThrows(
                        RuntimeException.class,
                        () -> itemService.delist(ITEM_ID, OWNER_ID)
                )
        );

        verify(itemRepository, never()).getById(anyLong());
        verify(itemRepository, never()).updateStatus(anyLong(), any());
    }

}
