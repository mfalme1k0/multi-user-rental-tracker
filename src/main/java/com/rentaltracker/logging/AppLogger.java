package com.rentaltracker.logging;

import java.util.Map;

// Provides a shared interface for recording structured application events.
// Application layers use this interface without depending on Logback or knowing how log events are persisted.
public interface AppLogger {

    // Records an informational event, such as a rental being created e.g. "rental.created".
    // Example: info("rental.created", Map.of("rental_id", 42, "item_id", 7)).
    void info(Long userId, String event, Map<String, ?> context);

    // Records a warning, such as a validation failure or recoverable issue.
    // Example: warn("validation.failed", Map.of("entity", "Item", "field", "cost")).
    void warn(Long userId, String event, Map<String, ?> context);

    // Records detailed diagnostic information.
    // Example: debug("repository.query", Map.of("operation", "item.find")).
    void debug(Long userId, String event, Map<String, ?> context);

    // Records an error and its associated exception.
    // Example: error("db.query", Map.of("operation", "rental.create"), exception).
    void error(
            Long userId,
            String event,
            Map<String, ?> context,
            Throwable cause
    );
}

