
package com.rentaltracker.transport.importing;

import com.rentaltracker.service.importing.ImportRecordResult;
import com.rentaltracker.service.importing.ImportService;
import com.rentaltracker.service.importing.ImportSummary;
import com.rentaltracker.service.importing.RejectedRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportCommandTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void printsAggregateCountsAndEveryRecordOutcome(@TempDir Path tempDir)
            throws Exception {
        Files.writeString(
                tempDir.resolve("users.csv"),
                "id,username,password,created_at\n"
        );

        Files.writeString(
                tempDir.resolve("listed_items.csv"),
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );

        Files.writeString(
                tempDir.resolve("rentals.csv"),
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );
        ImportSummary summary = new ImportSummary();
        summary.recordMatched("users.csv", 2);
        summary.recordInserted("listed_items.csv", 2);
        summary.reject(new RejectedRecord(
                "rentals.csv", 2, "item external id 9999 could not be resolved"
        ));

        ImportService importService = mock(ImportService.class);
        when(importService.importData(org.mockito.ArgumentMatchers.any()))
                .thenReturn(summary);

        redirectOutput();

        ImportCommand command = new ImportCommand(importService);
        int exitCode = command.run(new String[]{"import", tempDir.toString()});

        String output = stdout.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode);
        assertTrue(output.contains("inserted=1 matched=1 rejected=1"));
        assertTrue(output.contains("users.csv record 2: MATCHED"));
        assertTrue(output.contains("listed_items.csv record 2: INSERTED"));
        assertTrue(output.contains("rentals.csv record 2: REJECTED"));
        assertTrue(output.contains("item external id 9999 could not be resolved"));
    }

    private void redirectOutput() {
        stdout.reset();
        stderr.reset();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @Test
    void returnsTwoWhenArgumentsAreInvalid() {
        redirectOutput();

        ImportCommand command = new ImportCommand(mock(ImportService.class));

        int exitCode = command.run(new String[]{"import"});

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Usage: import <csv-directory>"));
    }

    @Test
    void returnsTwoWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
        redirectOutput();

        Path missingDirectory = tempDir.resolve("missing");

        ImportCommand command = new ImportCommand(mock(ImportService.class));

        int exitCode = command.run(
                new String[]{"import", missingDirectory.toString()}
        );

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Not a directory"));
    }
}
