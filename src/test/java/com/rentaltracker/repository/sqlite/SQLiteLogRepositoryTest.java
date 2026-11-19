package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.domain.User;
import com.rentaltracker.infrastructure.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SQLiteLogRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsAndReadsStructuredLogWithOptionalUser() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("logs.db"));
        manager.initialize();
        SQLiteLogRepository logs = new SQLiteLogRepository(manager);
        SQLiteUserRepository users = new SQLiteUserRepository(manager);
        User user = users.insert(User.newUser("alice", "hash"));

        LogEntry saved = logs.append(LogEntry.newEvent(
                LocalDateTime.of(2026, 10, 22, 6, 0, 0),
                "WARN",
                "validation.failed",
                "{\"entity\":\"Item\",\"field\":\"cost\"}",
                user.id()));

        assertEquals(1L, saved.id());
        assertEquals("WARN", saved.level());
        assertEquals("validation.failed", saved.eventName());
        assertEquals(user.id(), saved.userId());
        assertEquals(List.of(saved), logs.findRecent(10));
    }

    @Test
    void readsNewestEventsFirstAndHonorsLimit() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("ordered-logs.db"));
        manager.initialize();
        SQLiteLogRepository logs = new SQLiteLogRepository(manager);
        LogEntry first = logs.append(event(1));
        LogEntry second = logs.append(event(2));

        assertEquals(List.of(second), logs.findRecent(1));
        assertEquals(List.of(second, first), logs.findRecent(2));
    }

    @Test
    void rejectsNonPositiveReadLimit() {
        DatabaseManager manager = new DatabaseManager(tempDir.resolve("invalid-limit.db"));
        manager.initialize();
        SQLiteLogRepository logs = new SQLiteLogRepository(manager);

        assertThrows(IllegalArgumentException.class, () -> logs.findRecent(0));
    }

    private static LogEntry event(int minute) {
        return LogEntry.newEvent(
                LocalDateTime.of(2026, 10, 22, 6, minute, 0),
                "INFO",
                "test.event",
                "{}",
                null);
    }
}
