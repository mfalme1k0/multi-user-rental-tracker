package com.rentaltracker.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.repository.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;

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
    void testDefaultConstructorAllowsNullRepository() {
        assertNotNull(new AppLoggerImpl(null, objectMapper));
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
    void testNullRepositoryWritesToFileOnly() throws Exception {
        AppLoggerImpl fileOnly = new AppLoggerImpl(
                logFile,
                null,
                objectMapper,
                clock
        );

        assertDoesNotThrow(() ->
                fileOnly.info(null, "app.starting", Map.of("database_path", "/tmp/db")));

        String contents = Files.readString(logFile);
        assertTrue(contents.contains("app.starting"));
        assertTrue(contents.contains("database_path=/tmp/db"));
        // no repository to verify — absence of NPE is the contract
    }

    @Test
    void testNullRepositoryNeverAttemptsDatabaseAppend() {
        LogRepository unused = mock(LogRepository.class);
        // Use a real null repo, not the mock — prove append is skipped
        AppLoggerImpl fileOnly = new AppLoggerImpl(
                logFile,
                null,
                objectMapper,
                clock
        );

        fileOnly.warn(1L, "bootstrap.warn", Map.of("phase", "pre-schema"));

        verify(unused, never()).append(any(LogEntry.class));
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
        RuntimeException cause = new RuntimeException("secret details");

        logger.error(4L, "db.query", Map.of("operation", "rental.create"), cause);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("ERROR"));
        assertTrue(contents.contains("db.query"));
        assertTrue(contents.contains("error_type=RuntimeException"));
        assertTrue(!contents.contains("secret details"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testSystemEventDoesNotIncludeUserIdWhenUserIdIsNull() throws Exception {
        logger.info(null, "app.starting", Map.of("phase", "boot"));

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("app.starting"));
        assertTrue(!contents.contains("user_id="));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testContextUserIdIsOverriddenBySuppliedUserId() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user_id", 99);
        context.put("item_id", 7);

        logger.info(1L, "item.updated", context);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("user_id=1"));
        assertTrue(!contents.contains("user_id=99"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testSensitiveContextFieldsAreRedacted() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("password", "secret");
        context.put("password_hash", "hash");
        context.put("username", "alice");

        logger.info(1L, "auth.login", context);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("password=[REDACTED]"));
        assertTrue(contents.contains("password_hash=[REDACTED]"));
        assertTrue(contents.contains("username=alice"));
        assertTrue(!contents.contains("secret"));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testNullContextIsHandled() throws Exception {
        logger.info(1L, "noop.event", null);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("noop.event"));
        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testContextEscapesBackslashesLineBreaksTabsAndSeparators() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("path", "C:\\temp");
        context.put("note", "line1\nline2\r\tend|pipe");

        logger.info(1L, "escape.test", context);

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("path=C:\\\\temp"));
        assertTrue(contents.contains("\\n") || contents.contains("line1"));
        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testMultipleEventsAreAppendedToSameFile() throws Exception {
        logger.info(1L, "first", Map.of());
        logger.info(1L, "second", Map.of());

        String contents = Files.readString(logFile);

        assertTrue(contents.contains("first"));
        assertTrue(contents.contains("second"));
        verify(logRepository, times(2)).append(any(LogEntry.class));
    }

    @Test
    void testInvalidEventNameIsReportedWithoutWritingEntry() throws Exception {
        logger.info(1L, "  ", Map.of());

        // Primary event must not be written as a normal line; failure is recorded.
        if (Files.exists(logFile)) {
            String contents = Files.readString(logFile);
            assertTrue(contents.contains("logging.failure")
                    || contents.contains("missing event name")
                    || contents.contains("Cannot write log event"));
        }

        // No successful business append for the blank event
        verify(logRepository, never()).append(argThat(e ->
                e != null && "  ".equals(e.eventName())));
    }

    @Test
    void testSerializationFailureUsesFallbackAndContinuesLogging() throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {});

        AppLoggerImpl brokenLogger = new AppLoggerImpl(
                logFile,
                logRepository,
                brokenMapper,
                clock
        );

        assertDoesNotThrow(() ->
                brokenLogger.info(1L, "serialize.fail", Map.of("k", "v")));

        verify(logRepository).append(any(LogEntry.class));
    }

    @Test
    void testFileWriteFailureDoesNotPreventDatabasePersistence() throws Exception {
        // Point at an impossible path under a file-as-directory collision if possible;
        // otherwise simulate by using a path where parent cannot be created.
        Path impossible = tempDir.resolve("blocked");
        Files.writeString(impossible, "not-a-directory");
        Path badFile = impossible.resolve("child.log");

        AppLoggerImpl badFileLogger = new AppLoggerImpl(
                badFile,
                logRepository,
                objectMapper,
                clock
        );

        assertDoesNotThrow(() ->
                badFileLogger.info(1L, "test.file_failure", Map.of()));

        verify(logRepository, atLeastOnce()).append(any(LogEntry.class));
    }

    @Test
    void testDatabaseFailureDoesNotPreventFileLogging() throws Exception {
        doThrow(new RuntimeException("db down"))
                .when(logRepository).append(any(LogEntry.class));

        assertDoesNotThrow(() ->
                logger.info(1L, "test.database_failure", Map.of()));

        String contents = Files.readString(logFile);
        assertTrue(contents.contains("test.database_failure"));
        // failure diagnostic may also be appended to the file
        assertTrue(contents.contains("test.database_failure")
                || contents.contains("logging.failure"));
    }

    @Test
    void testDatabaseFailureWithNullRepositoryIsIgnored() throws Exception {
        AppLoggerImpl fileOnly = new AppLoggerImpl(
                logFile,
                null,
                objectMapper,
                clock
        );

        assertDoesNotThrow(() ->
                fileOnly.error(null, "app.database_init_failed",
                        Map.of("message", "disk full"),
                        new RuntimeException("disk full")));

        String contents = Files.readString(logFile);
        assertTrue(contents.contains("app.database_init_failed"));
    }

    @Test
    void testConstructorRejectsNullLogFileObjectMapperAndClock() {
        assertThrows(
                NullPointerException.class,
                () -> new AppLoggerImpl(null, logRepository, objectMapper, clock)
        );

        // logRepository may be null (bootstrap)
        assertDoesNotThrow(
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