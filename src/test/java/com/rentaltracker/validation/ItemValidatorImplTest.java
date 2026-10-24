package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemValidatorImplTest {

    private ItemValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ItemValidatorImpl();
    }

    @Test
    void testValidateNameReturnsTrimmedName() {
        assertEquals("Camera", validator.validateName("  Camera  "));
    }

    @Test
    void testValidateNameAcceptsNameWithoutWhitespace() {
        assertEquals("Camera", validator.validateName("Camera"));
    }

    @Test
    void testValidateNameRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateName(null)
        );
    }

    @Test
    void testValidateNameRejectsBlankName() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateName("   ")
        );
    }

    @Test
    void testValidateNameRejectsEmptyName() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateName("")
        );
    }

    @Test
    void testValidateCostPerDayAcceptsPositiveAmount() {
        BigDecimal cost = new BigDecimal("150.00");

        assertSame(cost, validator.validateCostPerDay(cost));
    }

    @Test
    void testValidateCostPerDayAcceptsSmallPositiveAmount() {
        BigDecimal cost = new BigDecimal("0.01");

        assertSame(cost, validator.validateCostPerDay(cost));
    }

    @Test
    void testValidateCostPerDayRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateCostPerDay(null)
        );
    }

    @Test
    void testValidateCostPerDayRejectsZero() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateCostPerDay(BigDecimal.ZERO)
        );
    }

    @Test
    void testValidateCostPerDayRejectsNegativeAmount() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateCostPerDay(new BigDecimal("-10.00"))
        );
    }
}
