package com.rentaltracker.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.repository.LogRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Writes human-readable log messages to a file and persists structured log events through LogRepository.
// Logging failures are reported to stderr rather than propagated into normal application operations.
public final class AppLoggerImpl implements AppLogger {

    // Defines the UTC timestamp format used in the log file.
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // Field names whose values must be redacted before logging.
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "password_hash",
            "passwordhash",
            "passwordHash",
            "current_password",
            "new_password"
    );

    private final Path logFile;
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Creates a logger using the configured file path and system UTC clock.
    // @param logRepository persists structured log events
    // @param objectMapper serializes event context as JSON
    public AppLoggerImpl(
            LogRepository logRepository,
            ObjectMapper objectMapper) {

        this(resolveLogFile(),
                logRepository,
                objectMapper,
                Clock.systemUTC()
        );
    }

    // Creates a logger with explicitly supplied dependencies.
    // @param logFile destination for readable log messages
    // @param logRepository persists structured log events
    // @param objectMapper serializes event context
    // @param clock supplies timestamps
    public AppLoggerImpl(
            Path logFile,
            LogRepository logRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // Resolves the log-file path from the environment.
    // Defaults to rental-tracker.log in the working directory if none provided.
    private static Path resolveLogFile() {
        return resolveLogFile(System.getenv("RENTAL_TRACKER_LOG_PATH"));
    }

    static Path resolveLogFile(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("rental-tracker.log");
        }

        return Path.of(configuredPath);
    }

    // Records a diagnostic event.
    // @param userId, ID of the user who performed the action, or null for system events
    // @param event, stable event name
    // @param context, structured event details
    @Override
    public void debug(Long userId, String event, Map<String, ?> context) {
        write(userId, "DEBUG", event, context, null);
    }

    // Records a normal application event.
    @Override
    public void info(Long userId, String event, Map<String, ?> context) {
        write(userId, "INFO", event, context, null);
    }

    // Records a warning about a recoverable problem.
    @Override
    public void warn(Long userId, String event, Map<String, ?> context) {
        write(userId, "WARN", event, context, null);
    }

    // Records an error without automatically exposing exception messages.
    // @param userId, associated user ID, or null for system events
    // @param event, stable event name
    // @param context, structured event details
    // @param cause, associated exception, or null
    @Override
    public void error(
            Long userId,
            String event,
            Map<String, ?> context,
            Throwable cause
    ) {
        write(userId, "ERROR", event, context, cause);
    }

    // Prepares a log event and sends it to the text file and database.
    // @param userId, ID of the user associated with the event, or null
    // @param level, severity of the event
    // @param event, stable event name
    // @param context, structured event details
    // @param cause, associated exception, or null
    private void write(
            Long userId,
            String level,
            String event,
            Map<String, ?> context,
            Throwable cause) {

        if (event == null || event.isBlank()) {
            reportLoggingFailure("Cannot write log event with a missing event name");
            return;
        }

        // Copy the context without retaining known sensitive values.
        Map<String, Object> safeContext = new LinkedHashMap<>();

        if (context != null) {
            safeContext.putAll(context);
        }

        for (String field : SENSITIVE_FIELDS) {
            if (safeContext.containsKey(field)) {
                safeContext.put(field, "[REDACTED]");
            }
        }

        // Use the supplied user ID as the authoritative actor identifier.
        safeContext.remove("user_id");

        if (userId != null) {
            safeContext.put("user_id", userId);
        }

        // Record only the exception type to avoid exposing sensitive messages.
        if (cause != null) {
            safeContext.put(
                    "error_type",
                    cause.getClass().getSimpleName()
            );
        }

        // Use the same UTC timestamp for both logging destinations.
        LocalDateTime occurredAt =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        // Serialize the structured context for database storage.
        String contextJson;

        try {
            contextJson = objectMapper.writeValueAsString(safeContext);
        } catch (JsonProcessingException exception) {
            reportLoggingFailure("Could not serialize log context");

            // Preserve a record that context serialization failed.
            contextJson = "{\"context_serialization_error\":true}";
        }

        // Format the event as a readable line for the log file.
        String line = timestamp
                + " | "
                + String.format("%-5s", level)
                + " | "
                + event
                + " | "
                + formatContext(safeContext)
                + System.lineSeparator();

        // Attempt each destination independently.
        writeToFile(line);

        writeToDatabase(
                occurredAt,
                level,
                event,
                contextJson,
                userId
        );
    }

    // Formats context entries as a space-separated key=value pairs.
    // Escapes line breaks and separators to preserve one event per line.
    // @param context event details
    private String formatContext(Map<String, ?> context) {
        StringJoiner joiner = new StringJoiner(" ");

        for (Map.Entry<String, ?> entry : context.entrySet()) {
            String value = String.valueOf(entry.getValue())
                    .replace("\\", "\\\\")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                    .replace("|", "\\|");

            joiner.add(entry.getKey() + "=" + value);
        }

        return joiner.toString();
    }


    // Appends a formatted log line to the file, creating directories and the file when necessary.
    // @param line, complete formatted log line
    // Appends a formatted log line to the file, creating directories and the file when necessary.
    private void writeToFile(String line) {
        try {
            createParentDirectories(logFile.toAbsolutePath().getParent());

            Files.writeString(
                    logFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | SecurityException exception) {
            reportLoggingFailure("Could not write to log file");
        }
    }

    // Creates parent directories when a parent path exists.
    static void createParentDirectories(Path parent) throws IOException {
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    // Persists the structured event, including its associated user ID.
    // @param occurredAt, time the event occurred
    // @param level, event severity
    // @param event, stable event name
    // @param contextJson, serialized event details
    // @param userId, associated user ID, or null for a system event
    private void writeToDatabase(
            LocalDateTime occurredAt,
            String level,
            String event,
            String contextJson,
            Long userId) {

        try {
            LogEntry entry = LogEntry.newEvent(
                    occurredAt,
                    level,
                    event,
                    contextJson,
                    userId
            );

            logRepository.append(entry);
        } catch (RuntimeException exception) {
            reportLoggingFailure("Could not persist log event: " + event
                            + " (" + exception.getClass().getSimpleName() + ")"
            );
        }
    }

   // Reports a logging failure directly to stderr to avoid recursive logging.
//    private void reportLoggingFailure(String message) {
//        System.err.println("[LOGGING FAILURE] " + message);
//    }

    // Records logging failures in the file and database without recursive reporting.
    private void reportLoggingFailure(String message) {
        LocalDateTime occurredAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        String safeMessage = String.valueOf(message)
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("|", "\\|");

        // Attempt to write the diagnostic directly to the log file.
        String line = timestamp
                + " | ERROR | logging.failure | message="
                + safeMessage
                + System.lineSeparator();

        try {
            createParentDirectories(logFile.toAbsolutePath().getParent());

            Files.writeString(
                    logFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | SecurityException ignored) {
            // Do not report this failure again to avoid recursion
        }

        // Attempt to persist the diagnostic directly to the database.
        try {
            String contextJson = objectMapper.writeValueAsString(
                    Map.of("message", message == null ? "" : message)
            );

            LogEntry entry = LogEntry.newEvent(
                    occurredAt,
                    "ERROR",
                    "logging.failure",
                    contextJson,
                    null
            );

            logRepository.append(entry);
        } catch (Exception ignored) {
            // Do not report this failure again to avoid recursion.
        }
    }
}
