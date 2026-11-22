package com.rentaltracker.service.importing;

//single row from users.csv
public record CsvUserRow(
        int lineNumber,
        String id,
        String username,
        String password,
        String createdAt
) {}