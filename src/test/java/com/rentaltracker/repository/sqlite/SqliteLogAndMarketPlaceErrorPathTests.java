package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.MappingException;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SQLiteLogAndMarketplaceErrorPathsTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SQLiteLogRepository logs;
    private SQLiteItemRepository items;

    @BeforeEach
    void setUp() {
        databaseManager = new DatabaseManager(tempDir.resolve("errors.db"));
        databaseManager.initialize();
        logs = new SQLiteLogRepository(databaseManager);
        items = new SQLiteItemRepository(databaseManager);
    }

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

    private static void assertPlainDatabaseException(Executable call) {
        DatabaseException e = assertThrows(DatabaseException.class, call);
        assertEquals(DatabaseException.class, e.getClass(), "wrong subtype: " + e.getClass());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    private static LogEntry event() {
        return LogEntry.newEvent(LocalDateTime.of(2026, 10, 22, 6, 0, 0), "INFO", "test.event", "{}", null);
    }

    // ---- log repository ---------------------------------------------------------

    @Test
    void logAppendSurfacesDatabaseFailures() {
        exec("DROP TABLE application_logs");

        assertPlainDatabaseException(() -> logs.append(event()));
    }

    @Test
    void logFindRecentSurfacesDatabaseFailures() {
        exec("DROP TABLE application_logs");

        assertPlainDatabaseException(() -> logs.findRecent(5));
    }

    @Test
    void logWithUnparsableTimestampIsAMappingFailure() {
        exec("INSERT INTO application_logs (occurred_at, level, event_name, context_json) "
                + "VALUES ('not-a-date', 'INFO', 'bad.row', '{}')");

        MappingException e = assertThrows(MappingException.class, () -> logs.findRecent(5));

        assertTrue(e.getMessage().contains("LogEntry"), e.getMessage());
        assertInstanceOf(RuntimeException.class, e.getCause());
    }

    @Test
    void logInsertIgnoredByATriggerIsReported() {
        exec("CREATE TRIGGER swallow BEFORE INSERT ON application_logs BEGIN SELECT RAISE(IGNORE); END");

        DatabaseException e = assertThrows(DatabaseException.class, () -> logs.append(event()));

        assertTrue(e.getCause().getMessage().contains("could not be found"), e.getCause().getMessage());
    }

    // ---- item repository: marketplace queries -----------------------------------

    @Test
    void browseSurfacesDatabaseFailures() {
        exec("DROP TABLE listed_items");

        assertPlainDatabaseException(() -> items.findAvailableExcludingOwner(1, 0, 5));
    }

    @Test
    void markRentedSurfacesDatabaseFailures() {
        exec("DROP TABLE listed_items");

        assertPlainDatabaseException(() -> items.markRentedIfAvailable(1));
    }
}
