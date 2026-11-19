package com.rentaltracker.validation;

public interface AuthValidator {
    String validateUsername(String username);

    void validatePassword(String password);
}
