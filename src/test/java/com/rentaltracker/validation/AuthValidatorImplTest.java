package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthValidatorImplTest {

    private AuthValidatorImpl authValidator;

    @BeforeEach
    void setUp() {
        authValidator = new AuthValidatorImpl();
    }

    @Test
    void testValidateUsernameReturnsTrimmedUsername() {
        String result = authValidator.validateUsername("  jackson  ");

        assertEquals("jackson", result);
    }

    @Test
    void testValidateUsernameRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validateUsername(null)
        );
    }

    @Test
    void testValidateUsernameRejectsEmptyString() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validateUsername("")
        );
    }

    @Test
    void testValidateUsernameRejectsWhiteSpaceOnly() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validateUsername("   ")
        );
    }

    @Test
    void testValidatePasswordAcceptsExactlyEightCharacters() {
        assertDoesNotThrow(
                () -> authValidator.validatePassword("abcdefgh")
        );
    }

    @Test
    void testValidatePasswordAcceptsMoreThanEightCharacters() {
        assertDoesNotThrow(
                () -> authValidator.validatePassword("abc123!@#xyz")
        );
    }

    @Test
    void testValidatePasswordRejectsSevenCharacters() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validatePassword("abcdefg")
        );
    }

    @Test
    void testValidatePasswordRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validatePassword(null)
        );
    }
}
