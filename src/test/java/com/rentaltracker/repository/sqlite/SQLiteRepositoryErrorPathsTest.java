package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.MappingException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paths a healthy database never takes: the database fails under the repository, a stored row
 * cannot be mapped, or an insert succeeds but nothing can be read back. Each one must surface
 * as the layer's own exception, never a raw {@link SQLException}.
 */
class SQLiteRepositoryErrorPathsTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 10, 0);

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SQLiteUserRepository users;
    private SQLiteItemRepository items;
    private SQLiteRentalRepository rentals;

    @BeforeEach
    void setUp() {
        databaseManager = new DatabaseManager(tempDir.resolve("errors.db"));
        databaseManager.initialize();
        users = new SQLiteUserRepository(databaseManager);
        items = new SQLiteItemRepository(databaseManager);
        rentals = new SQLiteRentalRepository(databaseManager);
    }

    /** Runs raw SQL on one connection (so a PRAGMA affects the statements after it). */
    private void exec(String... statements) {
        Connection connection = databaseManager.getConnection();
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            databaseManager.releaseConnection(connection);
        }
    }

    /** A failure that is not one of the four constraint kinds: exactly DatabaseException. */
    private static void assertPlainDatabaseException(Executable call) {
        DatabaseException e = assertThrows(DatabaseException.class, call);
        assertEquals(DatabaseException.class, e.getClass(), "wrong subtype: " + e.getClass());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    private static MappingException assertMappingFailure(Executable call) {
        return assertThrows(MappingException.class, call);
    }

    private long insertOwnerAndItem() {
        User owner = users.insert(User.newUser("owner"));
        return items.insert(Item.newListing(owner.id(), "Ladder", "tall", new BigDecimal("5"))).id();
    }

    // ---- the database fails underneath a read or update -------------------------

    @Test
    void userQueriesSurfaceDatabaseFailures() {
        exec("DROP TABLE users");

        assertPlainDatabaseException(() -> users.findById(1));
        assertPlainDatabaseException(() -> users.findByUsername("alice"));
        assertPlainDatabaseException(users::findFirst);
        assertPlainDatabaseException(() -> users.getById(1));
    }

    @Test
    void itemQueriesSurfaceDatabaseFailures() {
        exec("DROP TABLE listed_items");

        assertPlainDatabaseException(() -> items.findById(1));
        assertPlainDatabaseException(() -> items.findByOwnerId(1));
        assertPlainDatabaseException(() -> items.findByOwnerIdAndStatus(1, ItemStatus.AVAILABLE));
        assertPlainDatabaseException(() -> items.updateStatus(1, ItemStatus.RENTED));
        assertPlainDatabaseException(() -> items.getById(1));
    }

    @Test
    void rentalQueriesSurfaceDatabaseFailures() {
        exec("DROP TABLE rentals");

        assertPlainDatabaseException(() -> rentals.findById(1));
        assertPlainDatabaseException(() -> rentals.findByItemId(1));
        assertPlainDatabaseException(() -> rentals.findActiveByItemId(1));
        assertPlainDatabaseException(() -> rentals.findActiveDetailsByOwnerId(1));
        assertPlainDatabaseException(() -> rentals.updateStatus(1, RentalStatus.CLOSED, START));
        assertPlainDatabaseException(() -> rentals.getById(1));
    }

    // ---- a stored row cannot be mapped to a domain object -----------------------

    @Test
    void userWithUnparsableTimestampIsAMappingFailure() {
        exec("INSERT INTO users (username, created_at) VALUES ('x', 'not-a-date')");

        MappingException e = assertMappingFailure(() -> users.findById(1));
        assertMappingFailure(() -> users.findByUsername("x"));
        assertMappingFailure(users::findFirst);
        assertMappingFailure(() -> users.getById(1));
        assertTrue(e.getMessage().contains("User"), e.getMessage());
        assertInstanceOf(RuntimeException.class, e.getCause());
    }

    @Test
    void itemWithUnparsableTimestampIsAMappingFailure() {
        exec("INSERT INTO users (username) VALUES ('owner')",
                "INSERT INTO listed_items (owner_id, name, cost_per_day, status, created_at) "
                        + "VALUES (1, 'Ladder', '5', 'available', 'not-a-date')");

        MappingException e = assertMappingFailure(() -> items.findById(1));
        assertMappingFailure(() -> items.findByOwnerId(1));
        assertMappingFailure(() -> items.findByOwnerIdAndStatus(1, ItemStatus.AVAILABLE));
        assertTrue(e.getMessage().contains("Item"), e.getMessage());
    }

    @Test
    void itemWithUnknownStatusOrBadCostIsAMappingFailure() {
        // The CHECK constraints normally stop these rows; switch them off to simulate a
        // database written by something else (or an older, looser schema).
        exec("PRAGMA ignore_check_constraints = ON",
                "INSERT INTO users (username) VALUES ('owner')",
                "INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                        + "VALUES (1, 'BadStatus', '5', 'broken')",
                "INSERT INTO listed_items (owner_id, name, cost_per_day, status) "
                        + "VALUES (1, 'BadCost', 'abc', 'available')");

        MappingException status = assertMappingFailure(() -> items.findById(1));
        MappingException cost = assertMappingFailure(() -> items.findById(2));

        assertInstanceOf(IllegalArgumentException.class, status.getCause());
        assertInstanceOf(NumberFormatException.class, cost.getCause());
    }

    @Test
    void rentalWithUnparsableValuesIsAMappingFailure() {
        long itemId = insertOwnerAndItem();
        users.insert(User.newUser("renter"));
        // 'a' < 'b', so the end > start CHECK passes even though neither is a timestamp.
        exec("INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                + "VALUES (" + itemId + ", 2, 'a', 'b', 'active')");

        MappingException e = assertMappingFailure(() -> rentals.findById(1));
        assertMappingFailure(() -> rentals.findByItemId(itemId));
        assertMappingFailure(() -> rentals.findActiveByItemId(itemId));
        assertMappingFailure(() -> rentals.findActiveDetailsByOwnerId(1));
        assertTrue(e.getMessage().contains("Rental"), e.getMessage());
    }

    @Test
    void rentalWithUnparsableReturnTimeOrUnknownStatusIsAMappingFailure() {
        long itemId = insertOwnerAndItem();
        users.insert(User.newUser("renter"));
        exec("PRAGMA ignore_check_constraints = ON",
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, returned_at, status) "
                        + "VALUES (" + itemId + ", 2, '2026-10-01T10:00:00Z', '2026-10-02T10:00:00Z', "
                        + "'garbage', 'closed')",
                "INSERT INTO rentals (item_id, renter_id, start_time, end_time, status) "
                        + "VALUES (" + itemId + ", 2, '2026-10-03T10:00:00Z', '2026-10-04T10:00:00Z', 'overdue')");

        assertMappingFailure(() -> rentals.findById(1));
        MappingException status = assertMappingFailure(() -> rentals.findById(2));
        assertInstanceOf(IllegalArgumentException.class, status.getCause());
    }

    // ---- an insert "succeeds" but nothing can be read back ----------------------

    @Test
    void insertIgnoredByATriggerIsReportedForUsers() {
        exec("CREATE TRIGGER swallow BEFORE INSERT ON users BEGIN SELECT RAISE(IGNORE); END");

        DatabaseException e = assertThrows(DatabaseException.class,
                () -> users.insert(User.newUser("alice")));

        assertTrue(e.getCause().getMessage().contains("could not be found"), e.getCause().getMessage());
    }


    @Test
    void insertIgnoredByATriggerIsReportedForItems() {
        User owner = users.insert(User.newUser("owner"));
        exec("CREATE TRIGGER swallow BEFORE INSERT ON listed_items BEGIN SELECT RAISE(IGNORE); END");

        DatabaseException e = assertThrows(DatabaseException.class, () ->
                items.insert(Item.newListing(owner.id(), "Ladder", null, new BigDecimal("5"))));

        assertTrue(e.getCause().getMessage().contains("could not be found"), e.getCause().getMessage());
    }


    @Test
    void insertIgnoredByATriggerIsReportedForRentals() {
        long itemId = insertOwnerAndItem();
        User renter = users.insert(User.newUser("renter"));
        exec("CREATE TRIGGER swallow BEFORE INSERT ON rentals BEGIN SELECT RAISE(IGNORE); END");

        DatabaseException e = assertThrows(DatabaseException.class, () ->
                rentals.insert(Rental.newActive(itemId, renter.id(), START, START.plusDays(1))));

        assertTrue(e.getCause().getMessage().contains("could not be found"), e.getCause().getMessage());
    }

}