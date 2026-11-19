package com.rentaltracker.repository.sqlite;

import com.rentaltracker.exception.CheckConstraintException;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.ForeignKeyConstraintException;
import com.rentaltracker.exception.NotNullConstraintException;
import com.rentaltracker.exception.UniqueConstraintException;

import java.sql.SQLException;

public final class SQLiteExceptionTranslator {

    private SQLiteExceptionTranslator() {
    }

    public static DatabaseException translate(SQLException exception) {
        String message = exception.getMessage();

        if (message != null) {
            if (message.contains("UNIQUE constraint failed")) {
                return new UniqueConstraintException(
                        "A unique database constraint was violated",
                        exception
                );
            }

            if (message.contains("NOT NULL constraint failed")) {
                return new NotNullConstraintException(
                        "A required database value was missing",
                        exception
                );
            }

            if (message.contains("FOREIGN KEY constraint failed")) {
                return new ForeignKeyConstraintException(
                        "A foreign key constraint was violated",
                        exception
                );
            }

            if (message.contains("CHECK constraint failed")) {
                return new CheckConstraintException(
                        "A database check constraint was violated",
                        exception
                );
            }
        }

        return new DatabaseException(
                "A database operation failed",
                exception
        );
    }
}