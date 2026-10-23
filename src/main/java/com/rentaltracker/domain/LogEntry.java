package com.rentaltracker.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** A non-sensitive structured event persisted for operational and audit diagnostics. */
public record LogEntry(
        Long id,
        LocalDateTime occurredAt,
        String level,
        String eventName,
        String contextJson,
        Long userId
) {
    public LogEntry {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(contextJson, "contextJson");
    }

    public static LogEntry newEvent(LocalDateTime occurredAt, String level,
                                    String eventName, String contextJson, Long userId) {
        return new LogEntry(null, occurredAt, level, eventName, contextJson, userId);
    }
}
