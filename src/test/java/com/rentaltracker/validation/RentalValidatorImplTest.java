package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalValidatorImplTest {

    private RentalValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RentalValidatorImpl();
    }

    @Test
    void testValidateRentalDurationAcceptsOneDay() {
        assertEquals(1, validator.validateRentalDuration(1));
    }

    @Test
    void testValidateRentalDurationAcceptsMultipleDays() {
        assertEquals(7, validator.validateRentalDuration(7));
    }

    @Test
    void testValidateRentalDurationRejectsZeroDays() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateRentalDuration(0)
        );
    }

    @Test
    void testValidateRentalDurationRejectsNegativeDays() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateRentalDuration(-1)
        );
    }
}
