package com.rentaltracker.repository;

import com.rentaltracker.domain.LogEntry;

import java.util.List;

// Persistence contract for non-sensitive structured operational events.
public interface LogRepository {
    // Stores an event and returns it with its generated ID populated.
    LogEntry append(LogEntry entry);

    // Returns the newest events first, bounded by the supplied limit.
    List<LogEntry> findRecent(int limit);
}