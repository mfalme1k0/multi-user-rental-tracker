package com.rentaltracker.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherImplTest {
    private PasswordHasherImpl passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasherImpl();
    }

    @Test
    void testHashPasswordReturnsHashInsteadOfOriginalPassword() {
        String password = "SecurePass123";

        String passwordHash = passwordHasher.hashPassword(password);

        assertNotNull(passwordHash);
        assertNotEquals(password, passwordHash);
    }

    @Test
    void testMatchesReturnsTrueForCorrectPassword() {
        String password = "SecurePass123";
        String passwordHash = passwordHasher.hashPassword(password);

        boolean matches = passwordHasher.matches(password, passwordHash);

        assertTrue(matches);
    }

    @Test
    void testMatchesReturnsFalseForIncorrectPassword() {
        String passwordHash = passwordHasher.hashPassword("SecurePass123");

        boolean matches = passwordHasher.matches("WrongPass123", passwordHash);

        assertFalse(matches);
    }

}