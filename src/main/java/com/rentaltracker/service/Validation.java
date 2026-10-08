package com.rentaltracker.service;

import com.rentaltracker.exception.ValidationException;

import java.math.BigDecimal;

// Package-private input checks shared by the three services.
// Kept in one place so a rule (e.g. "minimum 1 day")is defined once and every service enforces the same wording of the error.
final class Validation {

    /** The minimum number of days for a rental. */
    static final int MIN_RENTAL_DAYS = 1;

    private Validation() {
    }

    static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    // The cost per day is a decimal and must be strictly positive.
    static BigDecimal requirePositive(BigDecimal costPerDay) {
        if (costPerDay == null || costPerDay.signum() <= 0) {
            throw new ValidationException("costPerDay must be a positive amount");
        }
        return costPerDay;
    }

    /** Team decision: minimum rental duration is 1 day. */
    static int requireAtLeastMinimumDays(int days) {
        if (days < MIN_RENTAL_DAYS) {
            throw new ValidationException("Rental duration must be at least " + MIN_RENTAL_DAYS + " day(s)");
        }
        return days;
    }
}
