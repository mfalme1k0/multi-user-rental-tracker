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
import java.util.Set;
import java.util.StringJoiner;


public final class AppLoggerImpl implements AppLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "password_hash",
            "passwordhash",
            "passwordHash",
            "current_password",
            "new_password"
    );

    private final Path logFile;
  //nullable for file only read before db
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;


    public AppLoggerImpl(LogRepository logRepository, ObjectMapper objectMapper) {
        this(resolveLogFile(), logRepository, objectMapper, Clock.systemUTC());
    }


    public AppLoggerImpl(
            Path logFile,
            LogRepository logRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.logRepository = logRepository; // intentionally nullable for bootstrap
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static Path resolveLogFile() {
        return resolveLogFile(System.getenv("RENTAL_TRACKER_LOG_PATH"));
    }

    static Path resolveLogFile(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("rental-tracker.log");
        }
        return Path.of(configuredPath);
    }

    @Override
    public void debug(Long userId, String event, Map<String, ?> context) {
        write(userId, "DEBUG", event, context, null);
    }

    @Override
    public void info(Long userId, String event, Map<String, ?> context) {
        write(userId, "INFO", event, context, null);
    }

    @Override
    public void warn(Long userId, String event, Map<String, ?> context) {
        write(userId, "WARN", event, context, null);
    }

    @Override
    public void error(Long userId, String event, Map<String, ?> context, Throwable cause) {
        write(userId, "ERROR", event, context, cause);
    }

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

        Map<String, Object> safeContext = new LinkedHashMap<>();
        if (context != null) {
            safeContext.putAll(context);
        }

        for (String field : SENSITIVE_FIELDS) {
            if (safeContext.containsKey(field)) {
                safeContext.put(field, "[REDACTED]");
            }
        }

        safeContext.remove("user_id");
        if (userId != null) {
            safeContext.put("user_id", userId);
        }

        if (cause != null) {
            safeContext.put("error_type", cause.getClass().getSimpleName());
        }

        LocalDateTime occurredAt =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(safeContext);
        } catch (JsonProcessingException exception) {
            reportLoggingFailure("Could not serialize log context");
            contextJson = "{\"context_serialization_error\":true}";
        }

        String line = timestamp
                + " | "
                + String.format("%-5s", level)
                + " | "
                + event
                + " | "
                + formatContext(safeContext)
                + System.lineSeparator();

        // File always; database only when a repository is wired.
        writeToFile(line);
        writeToDatabase(occurredAt, level, event, contextJson, userId);
    }

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

    private void writeToFile(String line) {
        try {
            createParentDirectories(logFile.toAbsolutePath().getParent());
            Files.writeString(
                    logFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | SecurityException exception) {
            reportLoggingFailure("Could not write to log file");
        }
    }

    static void createParentDirectories(Path parent) throws IOException {
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void writeToDatabase(
            LocalDateTime occurredAt,
            String level,
            String event,
            String contextJson,
            Long userId) {

        if (logRepository == null) {
            return; // bootstrap / file-only mode
        }

        try {
            LogEntry entry = LogEntry.newEvent(
                    occurredAt,
                    level,
                    event,
                    contextJson,
                    userId);
            logRepository.append(entry);
        } catch (RuntimeException exception) {
            reportLoggingFailure(
                    "Could not persist log event: " + event
                            + " (" + exception.getClass().getSimpleName() + ")");
        }
    }


    private void reportLoggingFailure(String message) {
        LocalDateTime occurredAt =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        String safeMessage = String.valueOf(message)
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("|", "\\|");

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
                    StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ignored) {
            // Avoid recursion.
        }

        if (logRepository == null) {
            return;
        }

        try {
            String contextJson = objectMapper.writeValueAsString(
                    Map.of("message", message == null ? "" : message));
            LogEntry entry = LogEntry.newEvent(
                    occurredAt,
                    "ERROR",
                    "logging.failure",
                    contextJson,
                    null);
            logRepository.append(entry);
        } catch (Exception ignored) {
            // Avoid recursion.
        }
    }
}