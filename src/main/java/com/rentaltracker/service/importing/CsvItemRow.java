package com.rentaltracker.service.importing;

//single row from items.csv
public record CsvItemRow(
        int lineNumber,
        String id,
        String ownerId,
        String name,
        String description,
        String costPerDay,
        String status,
        String createdAt
) {}