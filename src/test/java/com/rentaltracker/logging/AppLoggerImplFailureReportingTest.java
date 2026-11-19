package com.rentaltracker.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// A log call with no usable event name is reported as a failure, with and without a database.
class AppLoggerImplFailureReportingTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T10:30:45Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void nullEventWithoutRepositoryIsReportedToTheFileOnly() throws Exception {
        Path logFile = tempDir.resolve("file-only-null.log");
        AppLoggerImpl logger = new AppLoggerImpl(logFile, null, new ObjectMapper(), CLOCK);

        assertDoesNotThrow(() -> logger.info(1L, null, Map.of()));

        String contents = Files.readString(logFile);
        assertTrue(contents.contains("logging.failure"), contents);
        assertTrue(contents.contains("missing event name"), contents);
    }

    @Test
    void blankEventWithoutRepositoryIsReportedToTheFileOnly() throws Exception {
        Path logFile = tempDir.resolve("file-only-blank.log");
        AppLoggerImpl logger = new AppLoggerImpl(logFile, null, new ObjectMapper(), CLOCK);

        assertDoesNotThrow(() -> logger.warn(null, "   ", Map.of()));

        assertTrue(Files.readString(logFile).contains("logging.failure"));
    }

    @Test
    void nullEventWithRepositoryPersistsTheFailureDiagnostic() {
        LogRepository repository = mock(LogRepository.class);
        AppLoggerImpl logger = new AppLoggerImpl(
                tempDir.resolve("with-repo.log"), repository, new ObjectMapper(), CLOCK);

        logger.error(null, null, Map.of(), new RuntimeException("x"));

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(repository).append(captor.capture());
        LogEntry entry = captor.getValue();
        assertEquals("logging.failure", entry.eventName());
        assertEquals("ERROR", entry.level());
        assertTrue(entry.contextJson().contains("missing event name"), entry.contextJson());
    }
}
