package com.rentaltracker.auth;

import com.password4j.Hash;
import com.password4j.Password;

import java.util.Objects;

public class PasswordHasherImpl implements PasswordHasher {
    @Override
    public String hashPassword(String password) {
        Objects.requireNonNull(password, "Password must not be null");

        Hash hash = Password.hash(password).withBcrypt();

        return hash.getResult();
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        Objects.requireNonNull(password, "Password must not be null");
        Objects.requireNonNull(passwordHash, "Password hash must not be null");

        return Password.check(password, passwordHash)
                    .withBcrypt();
    }
}
