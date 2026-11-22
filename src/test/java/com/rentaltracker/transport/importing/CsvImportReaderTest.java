package com.rentaltracker.transport.importing;

import com.rentaltracker.service.importing.ImportBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CsvImportReaderTest {

    @TempDir
    Path directory;

    @Test
    void readsAllThreeCsvFiles() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n" +
                        "1,john,password123,2025-01-01T10:00:00\n"
        );

        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n" +
                        "10,1,Drill,\"Power drill, heavy duty\",500.00,AVAILABLE,2025-01-01T10:00:00\n"
        );

        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n" +
                        "100,10,1,2025-01-02T10:00:00,2025-01-03T10:00:00,,ACTIVE\n"
        );

        ImportBatch batch = new CsvImportReader(directory).readAll();

        assertAll(
                () -> assertEquals(1, batch.users().size()),
                () -> assertEquals(1, batch.items().size()),
                () -> assertEquals(1, batch.rentals().size())
        );

        assertAll(
                () -> assertEquals("1", batch.users().get(0).id()),
                () -> assertEquals("john", batch.users().get(0).username()),
                () -> assertEquals("10", batch.items().get(0).id()),
                () -> assertEquals(
                        "Power drill, heavy duty",
                        batch.items().get(0).description()
                ),
                () -> assertEquals("100", batch.rentals().get(0).id()),
                () -> assertEquals("ACTIVE", batch.rentals().get(0).status())
        );
    }

    @Test
    void readsHeaderOnlyFilesAsEmptyLists() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n"
        );

        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );

        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );

        ImportBatch batch = new CsvImportReader(directory).readAll();

        assertAll(
                () -> assertTrue(batch.users().isEmpty()),
                () -> assertTrue(batch.items().isEmpty()),
                () -> assertTrue(batch.rentals().isEmpty())
        );
    }

    private void writeCsv(String fileName, String content) throws IOException {
        Files.writeString(directory.resolve(fileName), content);
    }

    @Test
    void rejectsMissingUsersFile() throws IOException {
        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );
        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "missing CSV file: " + directory.resolve("users.csv"),
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingItemsFile() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n"
        );
        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "missing CSV file: " + directory.resolve("listed_items.csv"),
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingRentalsFile() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n"
        );
        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "missing CSV file: " + directory.resolve("rentals.csv"),
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingRequiredUserColumn() throws IOException {
        writeCsv("users.csv",
                "id,username,created_at\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "users.csv: missing required column 'password'",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingRequiredItemColumn() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n"
        );
        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,created_at\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "listed_items.csv: missing required column 'status'",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingRequiredRentalColumn() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n"
        );
        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );
        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at\n"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CsvImportReader(directory).readAll()
        );

        assertEquals(
                "rentals.csv: missing required column 'status'",
                exception.getMessage()
        );
    }

    @Test
    void preservesCsvRecordNumbers() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n" +
                        "1,john,password123,2025-01-01T10:00:00\n" +
                        "2,jane,password456,2025-01-02T10:00:00\n"
        );

        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );

        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );

        ImportBatch batch = new CsvImportReader(directory).readAll();

        assertAll(
                () -> assertEquals(1, batch.users().get(0).lineNumber()),
                () -> assertEquals(2, batch.users().get(1).lineNumber())
        );
    }

    @Test
    void wrapsMalformedCsvInUncheckedIOException() throws IOException {
        writeCsv("users.csv",
                "id,username,password,created_at\n" +
                        "1,\"john,password123,2025-01-01T10:00:00\n"
        );

        writeCsv("listed_items.csv",
                "id,owner_id,name,description,cost_per_day,status,created_at\n"
        );

        writeCsv("rentals.csv",
                "id,item_id,renter_id,start_time,end_time,returned_at,status\n"
        );

        java.io.UncheckedIOException exception = assertThrows(
                java.io.UncheckedIOException.class,
                () -> new CsvImportReader(directory).readAll()
        );
        assertAll(
                () -> assertNotNull(exception.getMessage()),
                () -> assertNotNull(exception.getCause()),
                () -> assertInstanceOf(IOException.class, exception.getCause()),
                () -> assertTrue(
                        exception.getCause().getMessage().contains("EOF reached")
                )
        );
    }
}