package com.rentaltracker.service;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.InvalidStateTransitionException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.validation.RentalValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long RENTER_ID = 2L;
    private static final long ITEM_ID = 10L;
    private static final long RENTAL_ID = 20L;
    private static final int RENTAL_DAYS = 5;

    private static final LocalDateTime FIXED_START =
            LocalDateTime.of(2026, 1, 1, 10, 0);

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private UserService userService;

    @Mock
    private Transactor transactor;

    @Mock
    private RentalValidator rentalValidator;

    @Mock
    private AppLogger logger;

    @Mock
    private Item item;

    @Mock
    private User owner;

    @Mock
    private User renter;

    private RentalService rentalService;

    @BeforeEach
    void setUp() {
        rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                rentalValidator,
                FIXED_CLOCK,
                logger
        );

        lenient().when(transactor.inTransaction(
                ArgumentMatchers.<Supplier<Rental>>any()
        )).thenAnswer(invocation -> {
            Supplier<Rental> work = invocation.getArgument(0);
            return work.get();
        });

        lenient().when(item.id()).thenReturn(ITEM_ID);
        lenient().when(item.ownerId()).thenReturn(OWNER_ID);
        lenient().when(item.status()).thenReturn(ItemStatus.AVAILABLE);
        lenient().when(owner.id()).thenReturn(OWNER_ID);
        lenient().when(renter.id()).thenReturn(RENTER_ID);
        lenient().when(rentalValidator.validateRentalDuration(RENTAL_DAYS))
                .thenReturn(RENTAL_DAYS);
    }

    @Test
    void testRecordRentalCreatesActiveRentalWithExpectedDates() {
        Rental insertedRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                null,
                RentalStatus.ACTIVE
        );

        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(RENTER_ID)).thenReturn(renter);
        when(itemRepository.markRentedIfAvailable(ITEM_ID)).thenReturn(true);
        when(rentalRepository.insert(any(Rental.class))).thenReturn(insertedRental);

        Rental result = rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS);

        assertSame(insertedRental, result);
        assertEquals(RentalStatus.ACTIVE, result.status());
        assertEquals(ITEM_ID, result.itemId());
        assertEquals(RENTER_ID, result.renterId());
        assertEquals(FIXED_START, result.startTime());
        assertEquals(FIXED_START.plusDays(RENTAL_DAYS), result.endTime());

        verify(rentalValidator).validateRentalDuration(RENTAL_DAYS);
        verify(itemRepository).markRentedIfAvailable(ITEM_ID);
        verify(rentalRepository).insert(any(Rental.class));
        verify(logger).info(eq(RENTER_ID), eq("rental.created"), anyMap());
    }

    @Test
    void testRecordRentalRejectsInvalidDurationBeforeAccessingRepositories() {
        ValidationException exception =
                new ValidationException("Rental duration must be at least 1 day");

        when(rentalValidator.validateRentalDuration(0)).thenThrow(exception);

        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, 0)
        );

        assertSame(exception, thrown);
        verify(logger).warn(eq(RENTER_ID), eq("validation.failed"), anyMap());
        verifyNoInteractions(itemRepository, rentalRepository, userService);
    }

    @Test
    void testRecordRentalRejectsOwnerRentingTheirOwnItem() {
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(OWNER_ID)).thenReturn(owner);

        assertThrows(
                BusinessRuleException.class,
                () -> rentalService.recordRental(ITEM_ID, OWNER_ID, RENTAL_DAYS)
        );

        verify(logger).warn(eq(OWNER_ID), eq("rental.rejected"), anyMap());
        verify(itemRepository, never()).markRentedIfAvailable(ITEM_ID);
        verify(rentalRepository, never()).insert(any(Rental.class));
    }

    @Test
    void testRecordRentalRejectsItemWithInvalidStatusTransition() {
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(RENTER_ID)).thenReturn(renter);

        InvalidStateTransitionException exception =
                new InvalidStateTransitionException("Invalid status transition");

        doThrow(exception).when(item).transitionTo(ItemStatus.RENTED);

        InvalidStateTransitionException thrown = assertThrows(
                InvalidStateTransitionException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS)
        );

        assertSame(exception, thrown);
        verify(logger).warn(eq(RENTER_ID), eq("rental.rejected"), anyMap());
        verify(itemRepository, never()).markRentedIfAvailable(ITEM_ID);
        verify(rentalRepository, never()).insert(any(Rental.class));
    }

    @Test
    void testRecordRentalRejectsItemThatBecomesUnavailable() {
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(RENTER_ID)).thenReturn(renter);
        when(itemRepository.markRentedIfAvailable(ITEM_ID)).thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS)
        );

        verify(logger).warn(eq(RENTER_ID), eq("rental.rejected"), anyMap());
        verify(rentalRepository, never()).insert(any(Rental.class));
    }

    @Test
    void testRecordRentalPropagatesItemNotFoundException() {
        NotFoundException exception = new NotFoundException("Item not found");

        when(itemRepository.getById(ITEM_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS)
        );

        assertSame(exception, thrown);
        verify(userService, never()).getUserById(RENTER_ID);
        verify(rentalRepository, never()).insert(any(Rental.class));
    }

    @Test
    void testRecordRentalPropagatesRenterNotFoundException() {
        NotFoundException exception = new NotFoundException("User not found");

        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(RENTER_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS)
        );

        assertSame(exception, thrown);
        verify(itemRepository, never()).markRentedIfAvailable(ITEM_ID);
        verify(rentalRepository, never()).insert(any(Rental.class));
    }

    @Test
    void testRecordRentalPropagatesRentalRepositoryFailure() {
        RuntimeException exception = new RuntimeException("Database failure");

        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(userService.getUserById(RENTER_ID)).thenReturn(renter);
        when(itemRepository.markRentedIfAvailable(ITEM_ID)).thenReturn(true);
        when(rentalRepository.insert(any(Rental.class))).thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> rentalService.recordRental(ITEM_ID, RENTER_ID, RENTAL_DAYS)
        );

        assertSame(exception, thrown);
    }

    @Test
    void testGetActiveRentalsReturnsActiveRentalDetailsForOwner() {
        RentalDetails details = new RentalDetails(
                new Rental(
                        RENTAL_ID,
                        ITEM_ID,
                        RENTER_ID,
                        FIXED_START,
                        FIXED_START.plusDays(RENTAL_DAYS),
                        null,
                        RentalStatus.ACTIVE
                ),
                "Camera",
                "renter"
        );

        List<RentalDetails> expected = List.of(details);

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.findActiveDetailsByOwnerId(OWNER_ID))
                .thenReturn(expected);

        List<RentalDetails> result = rentalService.getActiveRentals(OWNER_ID);

        assertSame(expected, result);
        assertEquals(1, result.size());
        assertSame(details, result.getFirst());
        verify(userService).getUserById(OWNER_ID);
        verify(rentalRepository).findActiveDetailsByOwnerId(OWNER_ID);
    }

    @Test
    void testGetActiveRentalsReturnsEmptyListWhenOwnerHasNoActiveRentals() {
        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.findActiveDetailsByOwnerId(OWNER_ID))
                .thenReturn(List.of());

        List<RentalDetails> result = rentalService.getActiveRentals(OWNER_ID);

        assertEquals(List.of(), result);
        verify(rentalRepository).findActiveDetailsByOwnerId(OWNER_ID);
    }

    @Test
    void testGetActiveRentalsPropagatesOwnerNotFoundException() {
        NotFoundException exception = new NotFoundException("User not found");

        when(userService.getUserById(OWNER_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.getActiveRentals(OWNER_ID)
        );

        assertSame(exception, thrown);
        verifyNoInteractions(rentalRepository);
    }

    @Test
    void testGetActiveRentalsPropagatesRentalRepositoryFailure() {
        RuntimeException exception = new RuntimeException("Database failure");

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.findActiveDetailsByOwnerId(OWNER_ID))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> rentalService.getActiveRentals(OWNER_ID)
        );

        assertSame(exception, thrown);
    }

    @Test
    void testConfirmReturnClosesActiveRentalAndMakesRentedItemAvailable() {
        Rental activeRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                null,
                RentalStatus.ACTIVE
        );

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.getById(RENTAL_ID)).thenReturn(activeRental);
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(item.status()).thenReturn(ItemStatus.RENTED);

        Item availableItem = Mockito.mock(Item.class);

        when(item.transitionTo(ItemStatus.AVAILABLE)).thenReturn(availableItem);
        when(availableItem.status()).thenReturn(ItemStatus.AVAILABLE);

        Rental result = rentalService.confirmReturn(RENTAL_ID, OWNER_ID);

        assertEquals(RentalStatus.CLOSED, result.status());
        assertEquals(FIXED_START, result.returnedAt());

        verify(rentalRepository).updateStatus(
                RENTAL_ID,
                RentalStatus.CLOSED,
                FIXED_START
        );
        verify(itemRepository).updateStatus(ITEM_ID, ItemStatus.AVAILABLE);
        verify(logger).info(
                eq(OWNER_ID),
                eq("rental.return_confirmed"),
                anyMap()
        );
    }

    @Test
    void testConfirmReturnKeepsDelistedItemUnlisted() {
        Rental activeRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                null,
                RentalStatus.ACTIVE
        );

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.getById(RENTAL_ID)).thenReturn(activeRental);
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);
        when(item.status()).thenReturn(ItemStatus.UNLISTED);

        Rental result = rentalService.confirmReturn(RENTAL_ID, OWNER_ID);

        assertEquals(RentalStatus.CLOSED, result.status());
        assertEquals(FIXED_START, result.returnedAt());

        verify(rentalRepository).updateStatus(
                RENTAL_ID,
                RentalStatus.CLOSED,
                FIXED_START
        );
        verify(itemRepository, never()).updateStatus(ITEM_ID, ItemStatus.AVAILABLE);
        verify(logger).info(
                eq(OWNER_ID),
                eq("rental.return_confirmed"),
                anyMap()
        );
    }

    @Test
    void testConfirmReturnRejectsRequestFromNonOwner() {
        Rental activeRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                null,
                RentalStatus.ACTIVE
        );

        when(userService.getUserById(RENTER_ID)).thenReturn(renter);
        when(rentalRepository.getById(RENTAL_ID)).thenReturn(activeRental);
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);

        assertThrows(
                BusinessRuleException.class,
                () -> rentalService.confirmReturn(RENTAL_ID, RENTER_ID)
        );

        verify(logger).warn(
                eq(RENTER_ID),
                eq("rental.return_rejected"),
                anyMap()
        );
        verify(rentalRepository, never()).updateStatus(
                eq(RENTAL_ID),
                any(RentalStatus.class),
                any(LocalDateTime.class)
        );
        verify(itemRepository, never()).updateStatus(
                eq(ITEM_ID),
                any(ItemStatus.class)
        );
    }

    @Test
    void testConfirmReturnRejectsRentalThatIsAlreadyClosed() {
        Rental closedRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                FIXED_START,
                RentalStatus.CLOSED
        );

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.getById(RENTAL_ID)).thenReturn(closedRental);
        when(itemRepository.getById(ITEM_ID)).thenReturn(item);

        assertThrows(
                BusinessRuleException.class,
                () -> rentalService.confirmReturn(RENTAL_ID, OWNER_ID)
        );

        verify(logger).warn(
                eq(OWNER_ID),
                eq("rental.return_rejected"),
                anyMap()
        );
        verify(rentalRepository, never()).updateStatus(
                eq(RENTAL_ID),
                any(RentalStatus.class),
                any(LocalDateTime.class)
        );
        verify(itemRepository, never()).updateStatus(
                eq(ITEM_ID),
                any(ItemStatus.class)
        );
    }

    @Test
    void testConfirmReturnPropagatesRequestingUserNotFoundException() {
        NotFoundException exception = new NotFoundException("User not found");

        when(userService.getUserById(OWNER_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.confirmReturn(RENTAL_ID, OWNER_ID)
        );

        assertSame(exception, thrown);
        verifyNoInteractions(rentalRepository, itemRepository);
    }

    @Test
    void testConfirmReturnPropagatesRentalNotFoundException() {
        NotFoundException exception = new NotFoundException("Rental not found");

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.getById(RENTAL_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.confirmReturn(RENTAL_ID, OWNER_ID)
        );

        assertSame(exception, thrown);
        verify(itemRepository, never()).getById(ITEM_ID);
    }

    @Test
    void testConfirmReturnPropagatesItemNotFoundException() {
        Rental activeRental = new Rental(
                RENTAL_ID,
                ITEM_ID,
                RENTER_ID,
                FIXED_START,
                FIXED_START.plusDays(RENTAL_DAYS),
                null,
                RentalStatus.ACTIVE
        );

        NotFoundException exception = new NotFoundException("Item not found");

        when(userService.getUserById(OWNER_ID)).thenReturn(owner);
        when(rentalRepository.getById(RENTAL_ID)).thenReturn(activeRental);
        when(itemRepository.getById(ITEM_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> rentalService.confirmReturn(RENTAL_ID, OWNER_ID)
        );

        assertSame(exception, thrown);
        verify(rentalRepository, never()).updateStatus(
                eq(RENTAL_ID),
                any(RentalStatus.class),
                any(LocalDateTime.class)
        );
    }
}
