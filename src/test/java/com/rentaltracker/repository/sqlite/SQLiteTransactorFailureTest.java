package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.DatabaseConnectionException;
import com.rentaltracker.exception.ForeignKeyConstraintException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SQLiteTransactorFailureTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SQLiteUserRepository users;
    private SQLiteItemRepository items;
    private SQLiteRentalRepository rentals;
    private SQLiteTransactor transactor;

    @BeforeEach
    void setUp() {
        databaseManager = new DatabaseManager(tempDir.resolve("failure.db"));
        databaseManager.initialize();

        users = new SQLiteUserRepository(databaseManager);
        items = new SQLiteItemRepository(databaseManager);
        rentals = new SQLiteRentalRepository(databaseManager);
        transactor = new SQLiteTransactor(databaseManager);
    }

    @Test
    void failedLaterWriteUndoesEarlierStatusChange() {
        User owner = users.insert(User.newUser("owner", "test-hash"));
        Item item = items.insert(Item.newListing(owner.id(), "Camera", null, new BigDecimal("10")));
        LocalDateTime start = LocalDateTime.of(2026, 10, 2, 10, 0);

        // Same shape as RentalService.recordRental: flip the item, then insert the
        // rental. Renter 9999 does not exist, so the insert fails.
        assertThrows(ForeignKeyConstraintException.class, () ->
                transactor.inTransaction(() -> {
                    items.updateStatus(item.id(), ItemStatus.RENTED);
                    rentals.insert(Rental.newActive(item.id(), 9999L, start, start.plusDays(1)));
                })
        );

        assertEquals(ItemStatus.AVAILABLE, items.getById(item.id()).status());
        assertTrue(rentals.findActiveByItemId(item.id()).isEmpty());
    }

    @Test
    void repositoriesInsideATransactionShareOneConnection() {
        // The item insert needs the owner row, which is uncommitted at that point,
        // so the foreign key can only pass if both writes use the same connection.
        transactor.inTransaction(() -> {
            User owner = users.insert(User.newUser("owner", "test-hash"));
            items.insert(Item.newListing(owner.id(), "Camera", null, new BigDecimal("10")));
        });

        User owner = users.findByUsername("owner").orElseThrow();
        assertEquals(1, items.findByOwnerId(owner.id()).size());
    }

    @Test
    void errorThatIsNotARuntimeExceptionStillRollsBackAndEndsTheTransaction() {
        assertThrows(AssertionError.class, () ->
                transactor.inTransaction(() -> {
                    users.insert(User.newUser("ghost", "test-hash"));
                    throw new AssertionError("not a RuntimeException");
                })
        );

        assertFalse(databaseManager.isTransactionActive());
        assertTrue(users.findByUsername("ghost").isEmpty());

        // A later call must start a fresh transaction and really commit.
        transactor.inTransaction(() -> users.insert(User.newUser("after", "test-hash")));

        DatabaseManager reopened = new DatabaseManager(tempDir.resolve("failure.db"));
        assertTrue(new SQLiteUserRepository(reopened).findByUsername("after").isPresent());
    }

    @Test
    void failedCommitReportsTheCommitErrorAndLeavesNoTransactionOpen() {
        // With deferred foreign keys the bad row is accepted now and rejected at COMMIT.
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                transactor.inTransaction(() -> insertItemWithMissingOwnerDeferred())
        );

        assertInstanceOf(DatabaseConnectionException.class, thrown);
        assertTrue(thrown.getMessage().contains("commit"), thrown.getMessage());
        assertNotNull(thrown.getCause());
        assertFalse(databaseManager.isTransactionActive());
    }

    private void insertItemWithMissingOwnerDeferred() {
        Connection connection = databaseManager.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA defer_foreign_keys = ON");
            statement.execute("""
                    INSERT INTO listed_items (owner_id, name, cost_per_day, status)
                    VALUES (999, 'Ghost', '1', 'available')
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Test
    void failedRollbackIsAttachedToTheOriginalFailure() {
        RuntimeException original = new RuntimeException("work failed");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                transactor.inTransaction(() -> {
                    closeTheTransactionConnection();   // the later ROLLBACK can no longer work
                    throw original;
                })
        );

        assertSame(original, thrown);                 // the real problem is not hidden
        assertEquals(1, thrown.getSuppressed().length);
        assertInstanceOf(DatabaseConnectionException.class, thrown.getSuppressed()[0]);
        assertFalse(databaseManager.isTransactionActive());
    }

    private void closeTheTransactionConnection() {
        try {
            databaseManager.getConnection().close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
