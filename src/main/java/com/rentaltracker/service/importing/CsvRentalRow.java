package com.rentaltracker.service.importing;

//single line from rentals.csv
public record CsvRentalRow(
        int lineNumber,
        String id,
        String itemId,
        String renterId,
        String startTime,
        String endTime,
        String returnedAt,
        String status
) {}