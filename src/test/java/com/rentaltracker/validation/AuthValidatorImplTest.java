package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthValidatorImplTest {

    private AuthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AuthValidatorImpl();
    }

    @Test
    void testValidateUsernameReturnsTrimmedUsername() {
        assertEquals("jackson", validator.validateUsername("  jackson  "));
    }

    @Test
    void testValidateUsernameAcceptsUsernameWithoutWhitespace() {
        assertEquals("jackson", validator.validateUsername("jackson"));
    }

    @Test
    void testValidateUsernameRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateUsername(null)
        );
    }

    @Test
    void testValidateUsernameRejectsBlankUsername() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateUsername("   ")
        );
    }

    @Test
    void testValidateUsernameRejectsEmptyUsername() {
        assertThrows(
                ValidationException.class,
                () -> validator.validateUsername("")
        );
    }

    @Test
    void testValidatePasswordAcceptsMinimumLength() {
        validator.validatePassword("12345678");
    }

    @Test
    void testValidatePasswordAcceptsLongerPassword() {
        validator.validatePassword("longer-password");
    }

    @Test
    void testValidatePasswordRejectsPasswordShorterThanMinimum() {
        assertThrows(
                ValidationException.class,
                () -> validator.validatePassword("1234567")
        );
    }

    @Test
    void testValidatePasswordRejectsEmptyPassword() {
        assertThrows(
                ValidationException.class,
                () -> validator.validatePassword("")
        );
    }

    @Test
    void testValidatePasswordRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> validator.validatePassword(null)
        );
    }
}
