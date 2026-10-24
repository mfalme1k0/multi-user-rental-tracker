package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthValidatorImplTest {

    private AuthValidatorImpl authValidator;

    @BeforeEach
    void setUp() {
        authValidator = new AuthValidatorImpl();
    }

    @Test
    void testValidateUsernameReturnsUsernameWithoutSurroundingWhitespace() {
        String result = authValidator.validateUsername("  jack  ");

        assertEquals("jack", result);
    }

    @Test
    void testValidateUsernameAcceptsUsernameWithoutWhitespace() {
        String result = authValidator.validateUsername("jack");

        assertEquals("jack", result);
    }

    @Test
    void testValidateUsernameRejectsNull() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validateUsername(null)
        );
    }

    @Test
    void testValidateUsernameRejectsBlankUsername() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validateUsername("   ")
        );
    }

    @Test
    void testValidatePasswordAcceptsExactlyEightCharacters() {
        authValidator.validatePassword("password");
    }

    @Test
    void testValidatePasswordAcceptsMoreThanEightCharacters() {
        authValidator.validatePassword("longpassword");
    }

    @Test
    void testValidatePasswordRejectsFewerThanEightCharacters() {
        assertThrows(
                ValidationException.class,
                () -> authValidator.validatePassword("short")
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