
package com.rentaltracker.transport.importing;

import com.rentaltracker.service.importing.ImportBatch;
import com.rentaltracker.service.importing.ImportService;
import com.rentaltracker.service.importing.ImportSummary;
import com.rentaltracker.service.importing.RejectedRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;


 // CLI entry point for the {import} subcommand.

 //Exit codes:
 //0 success (including partial rejections),
 //1 fatal error,
 // 2 command-line misuse.</p>

public final class ImportCommand {

    private final ImportService importService;

    public ImportCommand(ImportService importService) {
        this.importService = Objects.requireNonNull(importService);
    }

    public int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: import <csv-directory>");
            return 2;
        }

        Path directory = Path.of(args[1]);

        if (!Files.isDirectory(directory)) {
            System.err.println("Not a directory: " + directory);
            return 2;
        }

        try {
            return doImport(directory);
        } catch (RuntimeException e) {
            System.err.println("Import failed: " + e.getMessage());
            return 1;
        }
    }

    private int doImport(Path directory) {
        ImportBatch batch = new CsvImportReader(directory).readAll();

        ImportSummary summary = importService.importData(batch);

        printSummary(summary);

        return 0;
    }

    private void printSummary(ImportSummary summary) {
        System.out.printf(
                "Import complete: inserted=%d matched=%d rejected=%d%n",
                summary.inserted(),
                summary.matched(),
                summary.rejectedCount()
        );

        if (summary.rejectedCount() > 0) {
            System.out.println("Rejected:");

            for (RejectedRecord record : summary.rejected()) {
                System.out.println("  " + record);
            }
        }
    }
}
