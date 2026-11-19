package com.rentaltracker.auth;

public interface PasswordHasher {

    String hashPassword(String password);
    boolean matches(String password, String passwordHash);
}
