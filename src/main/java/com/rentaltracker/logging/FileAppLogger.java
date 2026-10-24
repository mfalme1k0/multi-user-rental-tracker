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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

// Writes human-readable log messages to a file and persists structured log events through LogRepository.
// Logging failures are reported to stderr rather than propagated into normal application operations.
public final class FileAppLogger implements AppLogger {

    // Defines the UTC timestamp format used in the log file.
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final Path logFile;
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Creates a logger using the configured file path and system UTC clock.
    // @param logRepository persists structured log events
    // @param objectMapper serializes event context as JSON
    public FileAppLogger(
            LogRepository logRepository,
            ObjectMapper objectMapper
    ) {
        this(
                resolveLogFile(),
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
    public FileAppLogger(
            Path logFile,
            LogRepository logRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.logRepository =
                Objects.requireNonNull(logRepository, "logRepository");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // Resolves the log-file path from the environment.
    // Defaults to rental-tracker.log in the working directory if none provided.
    private static Path resolveLogFile() {
        String configuredPath =
                System.getenv("RENTAL_TRACKER_LOG_PATH");

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
            Throwable cause
    ) {
        Objects.requireNonNull(event, "event");

        // Copy the context so the original map is not modified.
        Map<String, Object> safeContext = new LinkedHashMap<>();

        if (context != null) {
            safeContext.putAll(context);
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

        String timestamp = occurredAt
                .atOffset(ZoneOffset.UTC)
                .format(TIMESTAMP_FORMAT);

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
                    .replace("|", "\\|");

            joiner.add(entry.getKey() + "=" + value);
        }

        return joiner.toString();
    }

    // Appends a formatted log line to the file, creating directories and the file when necessary.
    // @param line, complete formatted log line
    private void writeToFile(String line) {
        try {
            Path parent = logFile.toAbsolutePath().getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

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
            reportLoggingFailure("Could not persist log event: " + event);
        }
    }

   // Reports a logging failure directly to stderr to avoid recursive logging.
    private void reportLoggingFailure(String message) {
        System.err.println("[LOGGING FAILURE] " + message);
    }
}
