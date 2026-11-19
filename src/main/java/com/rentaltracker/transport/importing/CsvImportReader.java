package com.rentaltracker.transport.importing;

import com.rentaltracker.service.importing.CsvItemRow;
import com.rentaltracker.service.importing.CsvRentalRow;
import com.rentaltracker.service.importing.CsvUserRow;
import com.rentaltracker.service.importing.ImportBatch;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class CsvImportReader {

    private static final String USERS_FILE   = "users.csv";
    private static final String ITEMS_FILE   = "listed_items.csv";
    private static final String RENTALS_FILE = "rentals.csv";

    private static final String[] USER_HEADERS =
            {"id", "username", "password", "created_at"};
    private static final String[] ITEM_HEADERS =
            {"id", "owner_id", "name", "description", "cost_per_day", "status", "created_at"};
    private static final String[] RENTAL_HEADERS =
            {"id", "item_id", "renter_id", "start_time", "end_time", "returned_at", "status"};

    private final Path directory;

    public CsvImportReader(Path directory) {
        this.directory = directory;
    }

    public ImportBatch readAll() {
        List<CsvUserRow>   users   = readUsers();
        List<CsvItemRow>   items   = readItems();
        List<CsvRentalRow> rentals = readRentals();
        return new ImportBatch(users, items, rentals);
    }

    private List<CsvUserRow> readUsers() {
        List<CsvUserRow> rows = new ArrayList<>();
        forEachRow(USERS_FILE, USER_HEADERS, record -> rows.add(new CsvUserRow(
                (int) record.getRecordNumber(),
                record.get("id"),
                record.get("username"),
                record.get("password"),
                record.get("created_at"))));
        return rows;
    }

    private List<CsvItemRow> readItems() {
        List<CsvItemRow> rows = new ArrayList<>();
        forEachRow(ITEMS_FILE, ITEM_HEADERS, record -> rows.add(new CsvItemRow(
                (int) record.getRecordNumber(),
                record.get("id"),
                record.get("owner_id"),
                record.get("name"),
                record.get("description"),
                record.get("cost_per_day"),
                record.get("status"),
                record.get("created_at"))));
        return rows;
    }

    private List<CsvRentalRow> readRentals() {
        List<CsvRentalRow> rows = new ArrayList<>();
        forEachRow(RENTALS_FILE, RENTAL_HEADERS, record -> rows.add(new CsvRentalRow(
                (int) record.getRecordNumber(),
                record.get("id"),
                record.get("item_id"),
                record.get("renter_id"),
                record.get("start_time"),
                record.get("end_time"),
                record.get("returned_at"),
                record.get("status"))));
        return rows;
    }

    private void forEachRow(String fileName,
                            String[] requiredHeaders,
                            RowConsumer consumer) {
        Path file = directory.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("missing CSV file: " + file);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)   // keep line numbers aligned
                .build();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (String header : requiredHeaders) {
                if (!parser.getHeaderMap().containsKey(header)) {
                    throw new IllegalStateException(
                            fileName + ": missing required column '" + header + "'");
                }
            }
            for (CSVRecord record : parser) {
                consumer.accept(record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(CSVRecord record);
    }
}