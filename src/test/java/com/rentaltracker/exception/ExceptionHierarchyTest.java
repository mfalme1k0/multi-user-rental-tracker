package com.rentaltracker.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the hierarchy the rest of the app catches against, and that every type carries message and cause. */
class ExceptionHierarchyTest {

    private static final Throwable CAUSE = new IllegalStateException("root cause");

    @Test
    void everyDatabaseFailureIsADatabaseException() {
        assertTrue(DatabaseException.class.isAssignableFrom(DatabaseConnectionException.class));
        assertTrue(DatabaseException.class.isAssignableFrom(NotFoundException.class));
        assertTrue(DatabaseException.class.isAssignableFrom(MappingException.class));
        assertTrue(DatabaseException.class.isAssignableFrom(ConstraintViolationException.class));
    }

    @Test
    void everyConstraintRuleIsAConstraintViolation() {
        assertTrue(ConstraintViolationException.class.isAssignableFrom(UniqueConstraintException.class));
        assertTrue(ConstraintViolationException.class.isAssignableFrom(ForeignKeyConstraintException.class));
        assertTrue(ConstraintViolationException.class.isAssignableFrom(NotNullConstraintException.class));
        assertTrue(ConstraintViolationException.class.isAssignableFrom(CheckConstraintException.class));
    }

    @Test
    void businessRuleFailuresAreNotDatabaseFailures() {
        assertTrue(BusinessRuleException.class.isAssignableFrom(InvalidStateTransitionException.class));
        assertTrue(BusinessRuleException.class.isAssignableFrom(ValidationException.class));
        assertFalse(DatabaseException.class.isAssignableFrom(BusinessRuleException.class));
        assertFalse(BusinessRuleException.class.isAssignableFrom(DatabaseException.class));
    }

    @Test
    void messageOnlyConstructorsHaveNoCause() {
        assertEquals("m", new DatabaseException("m").getMessage());
        assertNull(new DatabaseException("m").getCause());
        assertNull(new DatabaseConnectionException("m").getCause());
        assertNull(new NotFoundException("m").getCause());
        assertNull(new MappingException("m").getCause());
        assertNull(new UniqueConstraintException("m").getCause());
        assertNull(new ForeignKeyConstraintException("m").getCause());
        assertNull(new NotNullConstraintException("m").getCause());
        assertNull(new CheckConstraintException("m").getCause());
        assertEquals("m", new BusinessRuleException("m").getMessage());
        assertEquals("m", new InvalidStateTransitionException("m").getMessage());
        assertEquals("m", new ValidationException("m").getMessage());
    }

    @Test
    void causeConstructorsKeepTheOriginalSqlFailure() {
        assertSame(CAUSE, new DatabaseException("m", CAUSE).getCause());
        assertSame(CAUSE, new DatabaseConnectionException("m", CAUSE).getCause());
        assertSame(CAUSE, new NotFoundException("m", CAUSE).getCause());
        assertSame(CAUSE, new MappingException("m", CAUSE).getCause());
        assertSame(CAUSE, new UniqueConstraintException("m", CAUSE).getCause());
        assertSame(CAUSE, new ForeignKeyConstraintException("m", CAUSE).getCause());
        assertSame(CAUSE, new NotNullConstraintException("m", CAUSE).getCause());
        assertSame(CAUSE, new CheckConstraintException("m", CAUSE).getCause());
    }
}
