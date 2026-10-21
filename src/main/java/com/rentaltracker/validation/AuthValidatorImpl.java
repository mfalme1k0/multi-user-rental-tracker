package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;

public class AuthValidatorImpl implements AuthValidator {

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Override
    public String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username must not be blank");
        }

        // remove leading/trailing whitespace
        return username.strip();
    }

    @Override
    public void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
    }
}
