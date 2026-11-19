
package com.rentaltracker.service.importing;

import com.rentaltracker.domain.*;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportServiceTest {

    private UserRepository userRepository;
    private ItemRepository itemRepository;
    private RentalRepository rentalRepository;
    private Transactor transactor;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        itemRepository = mock(ItemRepository.class);
        rentalRepository = mock(RentalRepository.class);
        transactor = mock(Transactor.class);

        when(transactor.<ImportSummary>inTransaction(ArgumentMatchers.<Supplier<ImportSummary>>any()))
                .thenAnswer(invocation -> {
                    Supplier<ImportSummary> work =
                            invocation.getArgument(0);
                    return work.get();
                });

        importService = new ImportService(
                userRepository,
                itemRepository,
                rentalRepository,
                transactor
        );
    }

    @Test
    void insertsNewUser() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.empty());

        when(userRepository.insert(any(User.class)))
                .thenReturn(persistedUser);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(1, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(userRepository).findByUsername("john");

        verify(userRepository).insert(argThat(user ->
                user.id() == null
                        && user.username().equals("john")
                        && user.password().equals("password123")
        ));

        verifyNoInteractions(itemRepository, rentalRepository);
    }
    @Test
    void matchesExistingUserWithoutInsertingDuplicate() {
        User existingUser = new User(
                200L,
                "john",
                "existing-hash",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(existingUser));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(userRepository).findByUsername("john");
        verify(userRepository, never()).insert(any(User.class));

        verifyNoInteractions(itemRepository, rentalRepository);
    }
    @Test
    void rejectsUserWithBlankUsername() {
        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "  ", "password123", "2025-01-01"
                )),
                List.of(),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "users.csv line 2: username must not be blank",
                        summary.rejected().get(0).toString()
                )
        );

        verifyNoInteractions(userRepository, itemRepository, rentalRepository);
    }
    @Test
    void rejectsInsertedUserWithoutPersistedId() {
        User insertedUser = User.newUser("john", "password123");

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.empty());

        when(userRepository.insert(any(User.class)))
                .thenReturn(insertedUser);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "users.csv line 2: inserted user was not assigned a database ID",
                        summary.rejected().get(0).toString()
                )
        );

        verify(userRepository).insert(any(User.class));
    }
    @Test
    void rejectsUserWithInvalidExternalId() {
        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "not-a-number", "john", "password123", "2025-01-01"
                )),
                List.of(),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "users.csv line 2: id is not a valid integer: not-a-number",
                        summary.rejected().get(0).toString()
                )
        );

        verifyNoInteractions(userRepository, itemRepository, rentalRepository);
    }
    @Test
    void insertsNewItemUsingResolvedOwnerId() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item persistedItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.empty());

        when(userRepository.insert(any(User.class)))
                .thenReturn(persistedUser);

        when(itemRepository.findMatches(
                100L,
                "Drill",
                new BigDecimal("500.00"),
                "Power drill"
        )).thenReturn(List.of());

        when(itemRepository.insert(any(Item.class)))
                .thenReturn(persistedItem);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);
        System.out.println("Rejected records: " + summary.rejected());
        assertAll(
                () -> assertEquals(2, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(itemRepository).insert(argThat(item ->
                item.id() == null
                        && item.ownerId() == 100L
                        && item.name().equals("Drill")
                        && item.costPerDay().compareTo(new BigDecimal("500.00")) == 0
        ));

        verify(itemRepository).findMatches(
                100L,
                "Drill",
                new BigDecimal("500.00"),
                "Power drill"
        );
    }
    @Test
    void matchesExistingItemWithoutInsertingDuplicate() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L,
                "Drill",
                new BigDecimal("500.00"),
                "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(itemRepository).findMatches(
                100L,
                "Drill",
                new BigDecimal("500.00"),
                "Power drill"
        );

        verify(itemRepository, never()).insert(any(Item.class));
    }
    @Test
    void rejectsItemWithUnknownOwnerExternalId() {
        ImportBatch batch = new ImportBatch(
                List.of(),
                List.of(new CsvItemRow(
                        2, "10", "999", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "owner external id 999 could not be resolved",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(userRepository, itemRepository, rentalRepository);
    }
    @Test
    void rejectsItemWithInvalidCost() {
        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "0.00", "available", "2025-01-01"
                )),
                List.of()
        );

        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "cost_per_day must be positive",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(itemRepository);
    }
    @Test
    void rejectsItemWithUnknownStatus() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "broken", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "unknown item status: broken",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(itemRepository);
    }
    @Test
    void rejectsItemWhenMultipleMatchesAreFound() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item firstMatch = new Item(
                200L, 100L, "Drill", "Power drill",
                new BigDecimal("500.00"), ItemStatus.AVAILABLE, null
        );

        Item secondMatch = new Item(
                201L, 100L, "Drill", "Power drill",
                new BigDecimal("500.00"), ItemStatus.AVAILABLE, null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(firstMatch, secondMatch));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "ambiguous item match (multiple rows)",
                        summary.rejected().getFirst().reason()
                )
        );

        verify(itemRepository).findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        );
        verify(itemRepository, never()).insert(any(Item.class));
    }
    @Test
    void rejectsInsertedItemWithoutPersistedId() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item unsavedItem = new Item(
                null, 100L, "Drill", "Power drill",
                new BigDecimal("500.00"), ItemStatus.AVAILABLE, null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of());

        when(itemRepository.insert(any(Item.class)))
                .thenReturn(unsavedItem);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "inserted item was not assigned a database ID",
                        summary.rejected().getFirst().reason()
                )
        );

        verify(itemRepository).insert(any(Item.class));
    }
    @Test
    void normalizesBlankItemDescriptionToNull() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item persistedItem = new Item(
                200L,
                100L,
                "Drill",
                null,
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), null
        )).thenReturn(List.of());

        when(itemRepository.insert(any(Item.class)))
                .thenReturn(persistedItem);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "   ",
                        "500.00", "available", "2025-01-01"
                )),
                List.of()
        );

        ImportSummary summary = importService.importData(batch);
        System.out.println("Rejected records: " + summary.rejected());
        assertAll(
                () -> assertEquals(1, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(itemRepository).findMatches(
                100L, "Drill", new BigDecimal("500.00"), null
        );

        verify(itemRepository).insert(argThat(item ->
                item.description() == null
        ));
    }
    @Test
    void insertsNewRentalUsingResolvedItemAndRenterIds() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        Rental persistedRental = new Rental(
                300L,
                200L,
                100L,
                LocalDateTime.parse("2025-01-10T09:00:00"),
                LocalDateTime.parse("2025-01-12T09:00:00"),
                null,
                RentalStatus.ACTIVE
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        when(rentalRepository.findMatches(
                200L,
                100L,
                LocalDateTime.parse("2025-01-10T09:00:00")
        )).thenReturn(List.of());

        when(rentalRepository.insert(any(Rental.class)))
                .thenReturn(persistedRental);

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(1, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(rentalRepository).insert(argThat(rental ->
                rental.id() == null
                        && rental.itemId() == 200L
                        && rental.renterId() == 100L
                        && rental.startTime().equals(
                        LocalDateTime.parse("2025-01-10T09:00:00")
                )
                        && rental.endTime().equals(
                        LocalDateTime.parse("2025-01-12T09:00:00")
                )
                        && rental.returnedAt() == null
                        && rental.status() == RentalStatus.ACTIVE
        ));
    }
    @Test
    void matchesExistingRentalWithoutInsertingDuplicate() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        Rental existingRental = new Rental(
                300L,
                200L,
                100L,
                LocalDateTime.parse("2025-01-10T09:00:00"),
                LocalDateTime.parse("2025-01-12T09:00:00"),
                null,
                RentalStatus.ACTIVE
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        when(rentalRepository.findMatches(
                200L,
                100L,
                LocalDateTime.parse("2025-01-10T09:00:00")
        )).thenReturn(List.of(existingRental));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(3, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount())
        );

        verify(rentalRepository).findMatches(
                200L,
                100L,
                LocalDateTime.parse("2025-01-10T09:00:00")
        );

        verify(rentalRepository, never()).insert(any(Rental.class));
    }
    @Test
    void rejectsRentalWithUnknownItemExternalId() {
        ImportBatch batch = new ImportBatch(
                List.of(),
                List.of(),
                List.of(new CsvRentalRow(
                        2, "50", "999", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "item external id 999 could not be resolved",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(userRepository, itemRepository, rentalRepository);
    }
    @Test
    void rejectsRentalWithUnknownRenterExternalId() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "999",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "renter external id 999 could not be resolved",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    @Test
    void rejectsRentalWhenEndTimeIsNotAfterStartTime() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-12T09:00:00",
                        "2025-01-10T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "end_time must be after start_time",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    void rejectsRentalWhenEndTimeEqualsStartTime() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-10T09:00:00",
                        "",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "end_time must be after start_time",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    @Test
    void rejectsActiveRentalWithReturnTimestamp() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "2025-01-11T09:00:00",
                        "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "active rental must not have returned_at",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    @Test
    void rejectsClosedRentalWithoutReturnTimestamp() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "closed"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "closed rental must have returned_at",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    @Test
    void rejectsRentalWithUnknownStatus() {
        User persistedUser = new User(
                100L,
                "john",
                "password123",
                LocalDateTime.parse("2025-01-01T10:00:00")
        );

        Item existingItem = new Item(
                200L,
                100L,
                "Drill",
                "Power drill",
                new BigDecimal("500.00"),
                ItemStatus.AVAILABLE,
                null
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(persistedUser));

        when(itemRepository.findMatches(
                100L, "Drill", new BigDecimal("500.00"), "Power drill"
        )).thenReturn(List.of(existingItem));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Drill", "Power drill",
                        "500.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "50", "10", "1",
                        "2025-01-10T09:00:00",
                        "2025-01-12T09:00:00",
                        "",
                        "unknown"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(2, summary.matched()),
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        "unknown rental status: unknown",
                        summary.rejected().getFirst().reason()
                )
        );

        verifyNoInteractions(rentalRepository);
    }
    @Test
    void rejectsRentalWhenMultipleMatchesAreFound() {
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(
                        new User(100L, "john", "password123",
                                LocalDateTime.parse("2025-01-01T10:00:00"))
                ));

        when(itemRepository.findMatches(
                100L, "Camera", new BigDecimal("25.00"), null
        )).thenReturn(List.of(
                new Item(200L, 100L, "Camera", null,
                        new BigDecimal("25.00"), ItemStatus.AVAILABLE, null)
        ));

        when(rentalRepository.findMatches(
                eq(200L), eq(100L), any(LocalDateTime.class)
        )).thenReturn(List.of(
                new Rental(300L, 200L, 100L,
                        LocalDateTime.parse("2025-02-01T10:00:00"),
                        LocalDateTime.parse("2025-02-02T10:00:00"),
                        null, RentalStatus.ACTIVE),
                new Rental(301L, 200L, 100L,
                        LocalDateTime.parse("2025-02-01T10:00:00"),
                        LocalDateTime.parse("2025-02-03T10:00:00"),
                        null, RentalStatus.ACTIVE)
        ));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Camera", null,
                        "25.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "20", "10", "1",
                        "2025-02-01T10:00:00",
                        "2025-02-02T10:00:00",
                        "", "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        assertEquals(0, summary.inserted());
        assertEquals(2, summary.matched());
        assertEquals(1, summary.rejected().size());
        assertEquals(
                "ambiguous rental match (multiple rows)",
                summary.rejected().getFirst().reason()
        );

        verify(rentalRepository, never()).insert(any(Rental.class));
    }
    @Test
    void rejectsInsertedRentalWithoutPersistedId() {
        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(
                        new User(100L, "john", "password123",
                                LocalDateTime.parse("2025-01-01T10:00:00"))
                ));

        when(itemRepository.findMatches(
                100L, "Camera", new BigDecimal("25.00"), null
        )).thenReturn(List.of());

        when(itemRepository.insert(any(Item.class)))
                .thenReturn(new Item(
                        200L, 100L, "Camera", null,
                        new BigDecimal("25.00"), ItemStatus.AVAILABLE, null
                ));

        when(rentalRepository.findMatches(
                eq(200L), eq(100L), any(LocalDateTime.class)
        )).thenReturn(List.of());

        when(rentalRepository.insert(any(Rental.class)))
                .thenReturn(Rental.newActive(
                        200L, 100L,
                        LocalDateTime.parse("2025-02-01T10:00:00"),
                        LocalDateTime.parse("2025-02-02T10:00:00")
                ));

        ImportBatch batch = new ImportBatch(
                List.of(new CsvUserRow(
                        2, "1", "john", "password123", "2025-01-01"
                )),
                List.of(new CsvItemRow(
                        2, "10", "1", "Camera", null,
                        "25.00", "available", "2025-01-01"
                )),
                List.of(new CsvRentalRow(
                        2, "20", "10", "1",
                        "2025-02-01T10:00:00",
                        "2025-02-02T10:00:00",
                        "", "active"
                ))
        );

        ImportSummary summary = importService.importData(batch);

        System.out.println("Inserted: " + summary.inserted());
        System.out.println("Matched: " + summary.matched());
        System.out.println("Rejected: " + summary.rejected());

        assertEquals(1, summary.inserted());
        assertEquals(1, summary.matched());
        assertEquals(1, summary.rejected().size());
        assertEquals(
                "inserted rental was not assigned a database ID",
                summary.rejected().getFirst().reason()
        );

        verify(rentalRepository).insert(any(Rental.class));
    }
}
