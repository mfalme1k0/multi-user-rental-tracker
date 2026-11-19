package com.rentaltracker.repository.sqlite;

import com.rentaltracker.exception.CheckConstraintException;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.ForeignKeyConstraintException;
import com.rentaltracker.exception.NotNullConstraintException;
import com.rentaltracker.exception.UniqueConstraintException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class SQLiteExceptionTranslatorTest {

    @Test
    void translatesUniqueConstraint() {
        SQLException cause =
                new SQLException("UNIQUE constraint failed: users.username");

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertInstanceOf(UniqueConstraintException.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void translatesNotNullConstraint() {
        SQLException cause =
                new SQLException("NOT NULL constraint failed: users.username");

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertInstanceOf(NotNullConstraintException.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void translatesForeignKeyConstraint() {
        SQLException cause =
                new SQLException("FOREIGN KEY constraint failed");

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertInstanceOf(ForeignKeyConstraintException.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void translatesCheckConstraint() {
        SQLException cause =
                new SQLException("CHECK constraint failed");

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertInstanceOf(CheckConstraintException.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void translatesUnknownSqlExceptionToDatabaseException() {
        SQLException cause =
                new SQLException("Something unexpected happened");

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertInstanceOf(DatabaseException.class, result);
        assertSame(cause, result.getCause());
    }

    @Test
    void translatesExceptionWithoutMessageToDatabaseException() {
        SQLException cause = new SQLException();   // getMessage() is null

        DatabaseException result =
                SQLiteExceptionTranslator.translate(cause);

        assertEquals(DatabaseException.class, result.getClass());
        assertSame(cause, result.getCause());
    }
}