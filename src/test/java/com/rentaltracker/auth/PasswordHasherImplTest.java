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
    void testHashPasswordReturnsNonNullHash() {
        String hash = passwordHasher.hashPassword("password123");

        assertNotNull(hash);
        assertFalse(hash.isBlank());
    }

    @Test
    void testHashPasswordDoesNotReturnPlaintextPassword() {
        String password = "password123";

        String hash = passwordHasher.hashPassword(password);

        assertNotEquals(password, hash);
    }

    @Test
    void testHashPasswordGeneratesDifferentHashesForSamePassword() {
        String firstHash = passwordHasher.hashPassword("password123");
        String secondHash = passwordHasher.hashPassword("password123");

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void testMatchesReturnsTrueForCorrectPassword() {
        String password = "password123";
        String hash = passwordHasher.hashPassword(password);

        assertTrue(passwordHasher.matches(password, hash));
    }

    @Test
    void testMatchesReturnsFalseForIncorrectPassword() {
        String hash = passwordHasher.hashPassword("password123");

        assertFalse(passwordHasher.matches("wrongpassword", hash));
    }

    @Test
    void testHashPasswordRejectsNull() {
        assertThrows(
                NullPointerException.class,
                () -> passwordHasher.hashPassword(null)
        );
    }

    @Test
    void testMatchesRejectsNullPassword() {
        String hash = passwordHasher.hashPassword("password123");

        assertThrows(
                NullPointerException.class,
                () -> passwordHasher.matches(null, hash)
        );
    }

    @Test
    void testMatchesRejectsNullPasswordHash() {
        assertThrows(
                NullPointerException.class,
                () -> passwordHasher.matches("password123", null)
        );
    }
}