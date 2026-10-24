package com.rentaltracker.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.repository.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppLoggerImplTest {

    @TempDir
    Path tempDir;

    private LogRepository logRepository;
    private ObjectMapper objectMapper;
    private Clock clock;
    private Path logFile;
    private AppLoggerImpl logger;

    @BeforeEach
    void setUp() {
        logRepository = mock(LogRepository.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(
                Instant.parse("2026-01-15T10:30:45Z"),
                ZoneOffset.UTC
        );
        logFile = tempDir.resolve("logs").resolve("application.log");

        logger = new AppLoggerImpl(
                logFile,
                logRepository,
                objectMapper,
                clock
        );
    }

    @Test
    void testDefaultConstructorInitializesLogger() {
        AppLoggerImpl defaultLogger =
                new AppLoggerImpl(logRepository, objectMapper);

        assertNotNull(defaultLogger);
    }

    @Test
    void testInfoWritesInfoEventToFileAndDatabase() throws Exception {
        logger.info(1L, "item.created", Map.of("item_id", 10));

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("2026-01-15T10:30:45Z"));
        assertTrue(contents.contains("INFO"));
        assertTrue(contents.contains("item.created"));
        assertTrue(contents.contains("item_id=10"));
        assertTrue(contents.contains("user_id=1"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testWarnWritesWarningEvent() throws Exception {
        logger.warn(2L, "validation.failed", Map.of("field", "cost"));

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("WARN"));
        assertTrue(contents.contains("validation.failed"));
        assertTrue(contents.contains("field=cost"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testDebugWritesDiagnosticEvent() throws Exception {
        logger.debug(3L, "repository.query", Map.of("operation", "item.find"));

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("DEBUG"));
        assertTrue(contents.contains("repository.query"));
        assertTrue(contents.contains("operation=item.find"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testErrorWritesErrorEventAndExceptionTypeWithoutMessage() throws Exception {
        RuntimeException cause =
                new RuntimeException("sensitive exception details");

        logger.error(
                4L,
                "db.query",
                Map.of("operation", "rental.create"),
                cause
        );

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("ERROR"));
        assertTrue(contents.contains("db.query"));
        assertTrue(contents.contains("error_type=RuntimeException"));
        assertFalse(contents.contains("sensitive exception details"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testSystemEventDoesNotIncludeUserIdWhenUserIdIsNull() throws Exception {
        logger.info(null, "system.started", Map.of("component", "application"));

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("system.started"));
        assertFalse(contents.contains("user_id="));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testContextUserIdIsOverriddenBySuppliedUserId() throws Exception {
        logger.info(
                12L,
                "item.updated",
                Map.of("user_id", 999L, "item_id", 20)
        );

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("user_id=12"));
        assertFalse(contents.contains("user_id=999"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testSensitiveContextFieldsAreRedacted() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("password", "plain-secret");
        context.put("password_hash", "hash-secret");
        context.put("passwordhash", "hash-secret-2");
        context.put("passwordHash", "hash-secret-3");
        context.put("current_password", "old-secret");
        context.put("new_password", "new-secret");
        context.put("username", "jack");

        logger.info(1L, "auth.registration_succeeded", context);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("password=[REDACTED]"));
        assertTrue(contents.contains("password_hash=[REDACTED]"));
        assertTrue(contents.contains("passwordhash=[REDACTED]"));
        assertTrue(contents.contains("passwordHash=[REDACTED]"));
        assertTrue(contents.contains("current_password=[REDACTED]"));
        assertTrue(contents.contains("new_password=[REDACTED]"));
        assertTrue(contents.contains("username=jack"));

        assertFalse(contents.contains("plain-secret"));
        assertFalse(contents.contains("hash-secret"));
        assertFalse(contents.contains("old-secret"));
        assertFalse(contents.contains("new-secret"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testNullContextIsHandled() throws Exception {
        logger.info(1L, "system.started", null);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("system.started"));
        assertTrue(contents.contains("user_id=1"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testContextEscapesBackslashesLineBreaksTabsAndSeparators()
            throws Exception {
        logger.info(
                1L,
                "test.escaping",
                Map.of("value", "first\\second\rthird\nfourth\tfifth|sixth")
        );

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("value=first\\\\second\\rthird\\nfourth\\tfifth\\|sixth"));
        assertEquals(
                1,
                contents.lines().count(),
                "A log event should occupy exactly one line"
        );

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testMultipleEventsAreAppendedToSameFile() throws Exception {
        logger.info(1L, "event.first", Map.of());
        logger.warn(1L, "event.second", Map.of());
        logger.debug(1L, "event.third", Map.of());

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("event.first"));
        assertTrue(contents.contains("event.second"));
        assertTrue(contents.contains("event.third"));
        assertEquals(3, contents.lines().count());

        verify(logRepository, times(3)).append(any(LogEntry.class));
    }

    @Test
    void testInvalidEventNameIsReportedWithoutWritingEntry() throws Exception {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput));

            logger.info(1L, null, Map.of());
            logger.warn(1L, "   ", Map.of());

        } finally {
            System.setErr(originalError);
        }

        assertTrue(
                errorOutput.toString(StandardCharsets.UTF_8)
                        .contains("[LOGGING FAILURE]")
        );
        assertFalse(Files.exists(logFile));
        verifyNoInteractions(logRepository);
    }

    @Test
    void testSerializationFailureUsesFallbackAndContinuesLogging()
            throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);

        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") {});

        AppLoggerImpl failingLogger = new AppLoggerImpl(
                logFile,
                logRepository,
                failingMapper,
                clock
        );

        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput));

            failingLogger.info(1L, "test.serialization", Map.of("key", "value"));

        } finally {
            System.setErr(originalError);
        }

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("test.serialization"));
        assertTrue(
                errorOutput.toString(StandardCharsets.UTF_8)
                        .contains("Could not serialize log context")
        );

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testFileWriteFailureDoesNotPreventDatabasePersistence()
            throws Exception {
        Path blockingFile = tempDir.resolve("not-a-directory");
        Files.writeString(blockingFile, "existing file");

        Path impossibleLogPath = blockingFile.resolve("application.log");

        AppLoggerImpl failingLogger = new AppLoggerImpl(
                impossibleLogPath,
                logRepository,
                objectMapper,
                clock
        );

        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput));

            assertDoesNotThrow(
                    () -> failingLogger.info(1L, "test.file_failure", Map.of())
            );

        } finally {
            System.setErr(originalError);
        }

        assertTrue(
                errorOutput.toString(StandardCharsets.UTF_8)
                        .contains("Could not write to log file")
        );

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testDatabaseFailureDoesNotPreventFileLogging() throws Exception {
        doThrow(new RuntimeException("database unavailable"))
                .when(logRepository)
                .append(any(LogEntry.class));

        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput));

            assertDoesNotThrow(
                    () -> logger.info(1L, "test.database_failure", Map.of())
            );

        } finally {
            System.setErr(originalError);
        }

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("test.database_failure"));
        assertTrue(
                errorOutput.toString(StandardCharsets.UTF_8)
                        .contains("Could not persist log event")
        );

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testConstructorRejectsNullDependencies() {
        assertThrows(
                NullPointerException.class,
                () -> new AppLoggerImpl(null, logRepository, objectMapper, clock)
        );

        assertThrows(
                NullPointerException.class,
                () -> new AppLoggerImpl(logFile, null, objectMapper, clock)
        );

        assertThrows(
                NullPointerException.class,
                () -> new AppLoggerImpl(logFile, logRepository, null, clock)
        );

        assertThrows(
                NullPointerException.class,
                () -> new AppLoggerImpl(logFile, logRepository, objectMapper, null)
        );
    }

    @Test
    void testResolveLogFileUsesDefaultWhenPathIsNull() {
        assertEquals(
                Path.of("rental-tracker.log"),
                AppLoggerImpl.resolveLogFile(null)
        );
    }

    @Test
    void testResolveLogFileUsesDefaultWhenPathIsBlank() {
        assertEquals(
                Path.of("rental-tracker.log"),
                AppLoggerImpl.resolveLogFile("   ")
        );
    }

    @Test
    void testResolveLogFileUsesConfiguredPath() {
        assertEquals(
                Path.of("custom-rental-tracker.log"),
                AppLoggerImpl.resolveLogFile("custom-rental-tracker.log")
        );
    }

    @Test
    void testCreateParentDirectoriesWhenParentExists() throws IOException {
        Path parent = tempDir.resolve("nested").resolve("directory");

        AppLoggerImpl.createParentDirectories(parent);

        assertTrue(Files.isDirectory(parent));
    }

    @Test
    void testCreateParentDirectoriesWhenParentIsNull() throws IOException {
        assertDoesNotThrow(
                () -> AppLoggerImpl.createParentDirectories(null)
        );
    }
}
